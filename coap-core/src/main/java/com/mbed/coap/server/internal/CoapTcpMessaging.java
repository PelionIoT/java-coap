/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package com.mbed.coap.server.internal;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Coap server for reliable transport (draft-ietf-core-coap-tcp-tls-09)
 */
public class CoapTcpMessaging extends CoapMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTcpMessaging.class);

    private final ConcurrentMap<DelayedTransactionId, RequestCallback> transactions = new ConcurrentHashMap<>();
    private final CoapTcpCSMStorage csmStorage;
    private final CoapTcpCSM ownCapability;

    public CoapTcpMessaging(CoapTransport coapTransport, CoapTcpCSMStorage csmStorage, boolean useBlockWiseTransfer, int maxMessageSize) {
        super(coapTransport);
        this.csmStorage = csmStorage;
        this.ownCapability = new CoapTcpCSM(maxMessageSize, useBlockWiseTransfer);
    }

    @Override
    public void ping(InetSocketAddress destination, Callback<CoapPacket> callback) {
        CoapPacket pingRequest = new CoapPacket(Code.C702_PING, null, destination);
        makeRequest(pingRequest, callback, TransportContext.NULL);
    }

    @Override
    protected boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null) {
            LOGGER.debug("CoAP ping received.");
            // ignoring normal CoAP ping, according to https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4
            return true;
        }

        return handleSignal(packet);
    }

    private boolean handleSignal(CoapPacket packet) {
        if (packet.getCode() == null || !packet.getCode().isSignaling()) {
            return false;
        }

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
            CoapPacket pongResp = new CoapPacket(Code.C703_PONG, MessageType.Acknowledgement, packet.getRemoteAddress());
            pongResp.setToken(packet.getToken());
            sendPacket(pongResp, packet.getRemoteAddress(), TransportContext.NULL);
        } else if (packet.getCode() == Code.C703_PONG) {
            //handle this as reply
            return false;
        } else if (packet.getCode() == Code.C705_ABORT) {
            onDisconnected(packet.getRemoteAddress());

        } else {
            LOGGER.debug("[{}] Ignored signal message: {}", packet.getRemoteAddrString(), packet.getCode());
        }

        return true;
    }

    @Override
    public void makeRequest(CoapPacket packet, Callback<CoapPacket> callback, TransportContext transContext) {

        int payloadLen = packet.getPayload().length;
        int maxMessageSize = csmStorage.getOrDefault(packet.getRemoteAddress()).getMaxMessageSizeInt();

        if (payloadLen > maxMessageSize) {
            callback.callException(
                    new CoapException("Request payload size is too big and no block transfer support is enabled for " + packet.getRemoteAddress() + ": " + payloadLen)
            );
            return;
        }

        RequestCallback requestCallback = wrapCallback(callback);

        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        transactions.put(transId, requestCallback);


        sendPacket(packet, packet.getRemoteAddress(), transContext)
                .whenComplete((wasSent, maybeError) -> {
                    if (maybeError == null) {
                        requestCallback.onSent();
                    } else {
                        removeTransactionExceptionally(transId, (Exception) maybeError);
                    }
                });
    }

    @Override
    public void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext) {
        sendPacket(response, response.getRemoteAddress(), transContext);
    }

    @Override
    protected boolean handleDelayedResponse(CoapPacket packet) {
        return false;
    }

    @Override
    protected boolean handleResponse(CoapPacket packet) {
        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        RequestCallback callback = transactions.remove(transId);

        if (callback != null) {
            callback.call(packet);
            return true;
        }

        return false;
    }


    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        CoapPacket packet = new CoapPacket(remoteAddress);
        packet.setCode(Code.C701_CSM);

        packet.headers().putSignallingOptions(
                SignalingOptions.capabilities(ownCapability.getMaxMessageSizeInt(), ownCapability.isBlockTransferEnabled())
        );
        LOGGER.info("[" + remoteAddress + "] CoAP sent [" + packet.toString(false, false, false, true) + "]");
        coapTransporter.sendPacket(packet, remoteAddress, TransportContext.NULL);

        super.onConnected(remoteAddress);
    }

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        csmStorage.remove(remoteAddress);

        Set<DelayedTransactionId> trans = transactions.keySet();
        for (DelayedTransactionId transId : trans) {
            if (transId.hasRemoteAddress(remoteAddress)) {
                removeTransactionExceptionally(transId, new IOException("Socket closed"));
            }
        }

        super.onDisconnected(remoteAddress);
    }

    private void removeTransactionExceptionally(DelayedTransactionId transId, Exception error) {
        RequestCallback requestCallback = transactions.remove(transId);
        if (requestCallback != null) {
            requestCallback.callException(error);
        }
    }

    @Override
    protected void stop0() {
        // no additional stop hooks
    }

    @Override
    public void makePrioritisedRequest(CoapPacket packet, Callback<CoapPacket> callback, TransportContext transContext) {
        //not applicable in TCP transport
        makeRequest(packet, callback, transContext);
    }

}
