/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
import static com.mbed.coap.transport.CoapTransport.*;
import static com.mbed.coap.utils.CoapServerUtils.*;
import static com.mbed.coap.utils.FutureHelpers.*;
import static java.util.Objects.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base functionality for CoAP protocol servers
 */
public abstract class CoapMessaging implements CoapReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapMessaging.class);

    final CoapTransport transport;

    protected Function<SeparateResponse, Boolean> observationHandler;
    private Service<CoapRequest, CoapResponse> inboundService;

    private boolean isRunning;

    public abstract CompletableFuture<CoapResponse> send(CoapRequest req);

    public abstract CompletableFuture<Boolean> send(final SeparateResponse resp);

    protected abstract void sendResponse(CoapPacket request, CoapPacket response);


    public CoapMessaging(CoapTransport coapTransport) {
        this.transport = coapTransport;
    }

    public void init(Function<SeparateResponse, Boolean> observationHandler, Service<CoapRequest, CoapResponse> inboundService) {
        this.observationHandler = observationHandler;
        this.inboundService = inboundService;
    }

    @Override
    public synchronized void start() throws IllegalStateException {
        requireNonNull(observationHandler);
        requireNonNull(inboundService);
        assertNotRunning();
        isRunning = true;
    }

    private void assertNotRunning() {
        assume(!isRunning, "CoapProtoServer is running");
    }

    /**
     * Stops CoAP server
     *
     * @throws IllegalStateException if server is already stopped
     */
    @Override
    public synchronized void stop() throws IllegalStateException {
        if (!isRunning) {
            throw new IllegalStateException("CoapServer is not running");
        }

        isRunning = false;
        LOGGER.trace("Stopping CoapMessaging: {}", this);
        stop0();
        inboundService = null;
        observationHandler = null;

        LOGGER.debug("CoapMessaging stopped: {}", this);
    }

    protected abstract void stop0();

    protected final CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket) {
        return transport
                .sendPacket(coapPacket)
                .whenComplete((__, throwable) -> logSent(coapPacket, throwable));
    }

    @Override
    public void handle(CoapPacket packet) {
        logReceived(packet);
        if (handlePing(packet)) {
            return;
        }

        if (packet.getMethod() != null && packet.getMessageType() != MessageType.Acknowledgement) {
            handleRequest(packet);
            return;
        } else {
            if (handleResponse(packet)) {
                return;
            } else if (packet.isSeparateResponse() && handleDelayedResponse(packet)) {
                return;
            } else if (packet.isSeparateResponse() && packet.headers().getObserve() != null) {
                handleObservation(packet);
                return;
            }
        }
        //cannot process
        handleNotProcessedMessage(packet);
    }

    protected boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null && packet.getMessageType() == MessageType.Confirmable) {
            LOGGER.debug("CoAP ping received.");
            CoapPacket resp = packet.createResponse(null);
            resp.setMessageType(MessageType.Reset);
            sendResponse(packet, resp);
            return true;
        }
        return false;
    }

    protected abstract void handleNotProcessedMessage(CoapPacket packet);

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        LOGGER.debug("[{}] Disconnected", remoteAddress);
    }

    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        LOGGER.debug("[{}] Connected", remoteAddress);
    }

    protected abstract boolean handleDelayedResponse(CoapPacket packet);

    protected abstract boolean handleResponse(CoapPacket packet);

    protected void handleRequest(CoapPacket packet) {
        inboundService.apply(packet.toCoapRequest())
                .thenAccept(resp ->
                        sendResponse(packet, packet.createResponseFrom(resp))
                ).exceptionally(logError(LOGGER));
    }

    protected abstract void handleObservation(CoapPacket packet);

}
