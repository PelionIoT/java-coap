/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.server.messaging;

import static com.mbed.coap.transport.CoapReceiver.*;
import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapTcpDispatcher implements CoapReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTcpDispatcher.class);

    private final CoapTcpCSMStorage csmStorage;
    private final CoapTcpCSM ownCapability;
    private final Service<CoapPacket, Boolean> sender;
    private final Service<CoapRequest, CoapResponse> inboundService;
    private final Function<SeparateResponse, Boolean> outboundHandler;
    private final Function<SeparateResponse, Boolean> observationHandler;

    public CoapTcpDispatcher(Service<CoapPacket, Boolean> sender, CoapTcpCSMStorage csmStorage, CoapTcpCSM ownCapability,
            Service<CoapRequest, CoapResponse> inboundService, Function<SeparateResponse, Boolean> outboundHandler,
            Function<SeparateResponse, Boolean> observationHandler) {
        this.csmStorage = csmStorage;
        this.ownCapability = ownCapability;
        this.sender = sender;
        this.inboundService = inboundService;
        this.outboundHandler = outboundHandler;
        this.observationHandler = observationHandler;
    }

    @Override
    public void handle(CoapPacket packet) {
        logReceived(packet);

        // EMPTY (healthcheck)
        if (packet.getCode() == null && packet.getMethod() == null) {
            // ignore
            return;
        }

        // SIGNAL
        if (packet.getCode() != null && packet.getCode().isSignaling() && packet.getCode() != Code.C703_PONG) {
            onSignal(packet);
            return;
        }

        // REQUEST
        if (packet.getMethod() != null) {
            inboundService.apply(packet.toCoapRequest())
                    .thenAccept(resp -> sender.apply(packet.createResponseFrom(resp)))
                    .exceptionally(logError(LOGGER));
            return;
        }

        // RESPONSE
        if (packet.getCode() != null) {
            SeparateResponse resp = packet.toCoapResponse().toSeparate(packet.getToken(), packet.getRemoteAddress(), packet.getTransportContext());
            if (outboundHandler.apply(resp)) {
                return;
            }

            // OBSERVATION
            if (packet.headers().getObserve() != null && observationHandler.apply(resp)) {
                return;
            }
        }

        //cannot process
        LOGGER.warn("Can not process CoAP message [{}]", packet);
    }

    private void onSignal(CoapPacket packet) {
        if (packet.getCode() == Code.C701_CSM) {
            SignalingOptions signalingOpts = packet.headers().toSignallingOptions(packet.getCode());
            CoapTcpCSM remoteCapabilities = CoapTcpCSM.BASE;
            if (signalingOpts != null) {
                Long maxMessageSize = signalingOpts.getMaxMessageSize();
                Boolean blockWiseTransferBERT = signalingOpts.getBlockWiseTransfer();
                remoteCapabilities = CoapTcpCSM.BASE.withNewOptions(maxMessageSize, blockWiseTransferBERT);
            }
            csmStorage.put(packet.getRemoteAddress(), CoapTcpCSM.min(ownCapability, remoteCapabilities));

        } else if (packet.getCode() == Code.C702_PING) {
            CoapPacket pongResp = new CoapPacket(packet.getRemoteAddress());
            pongResp.setCode(Code.C703_PONG);
            pongResp.setToken(packet.getToken());
            sender.apply(pongResp);

        } else if (packet.getCode() == Code.C705_ABORT) {
            removeConnection(packet.toCoapResponse().toSeparate(packet.getToken(), packet.getRemoteAddress()));

        } else {
            LOGGER.debug("[{}] Ignored signal message: {}", packet.getRemoteAddrString(), packet.getCode());
        }
    }

    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        CoapPacket packet = new CoapPacket(remoteAddress);
        packet.setCode(Code.C701_CSM);

        packet.headers().putSignallingOptions(
                SignalingOptions.capabilities(ownCapability.getMaxMessageSizeInt(), ownCapability.isBlockTransferEnabled())
        );
        sender.apply(packet); // .exceptionally(logError(LOGGER));
    }

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        removeConnection(CoapResponse.of(Code.C705_ABORT).toSeparate(Opaque.EMPTY, remoteAddress));
    }

    @Override
    public void start() {
        // nothing to start
    }

    @Override
    public void stop() {
        // nothing to stop
    }


    private void removeConnection(SeparateResponse abortResp) {
        csmStorage.remove(abortResp.getPeerAddress());

        outboundHandler.apply(abortResp);
        LOGGER.debug("[{}] Disconnected", abortResp.getPeerAddress());
    }

}
