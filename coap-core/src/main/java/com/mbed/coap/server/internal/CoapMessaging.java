/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.internal;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by olesmi01 on 14.08.2017.
 * Base functionality for CoAP protocol servers
 */
public abstract class CoapMessaging implements CoapReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapMessaging.class);

    final CoapTransport coapTransporter;

    private CoapRequestHandler coapRequestHandler;
    private Consumer<InetSocketAddress> connectedHandler;

    private boolean isRunning;

    public abstract void ping(InetSocketAddress destination, final Callback<CoapPacket> callback);

    public abstract void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext);

    public abstract void makePrioritisedRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext);

    public abstract void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext);


    public CoapMessaging(CoapTransport coapTransport) {
        this.coapTransporter = coapTransport;
    }

    public synchronized void start(CoapRequestHandler coapRequestHandler) throws IllegalStateException, IOException {
        assertNotRunning();
        this.coapRequestHandler = coapRequestHandler;
        coapTransporter.start(this);
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
    public synchronized void stop() throws IllegalStateException {
        if (!isRunning) {
            throw new IllegalStateException("CoapServer is not running");
        }

        isRunning = false;
        LOGGER.trace("Stopping CoapMessaging: {}", this);
        stop0();
        coapTransporter.stop();
        coapRequestHandler = null;

        LOGGER.debug("CoapMessaging stopped: {}", this);
    }

    protected abstract void stop0();

    protected final CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        return coapTransporter
                .sendPacket(coapPacket, adr, tranContext)
                .whenComplete((__, throwable) -> logCoapSent(coapPacket, throwable));
    }

    private void logCoapSent(CoapPacket coapPacket, Throwable maybeError) {
        if (maybeError != null) {
            LOGGER.warn("[{}] CoAP sent failed [{}] {}", coapPacket.getRemoteAddress(), coapPacket.toString(false, false, false, true), maybeError.toString());
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("CoAP sent [{}]", coapPacket.toString(true));
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("CoAP sent [{}]", coapPacket.toString(false));
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[{}] CoAP sent [{}]", coapPacket.getRemoteAddress(), coapPacket.toString(false, false, false, true));
        }
    }

    @Override
    public void handle(CoapPacket packet, TransportContext transportContext) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("CoAP received [" + packet.toString(true) + "]");
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[" + packet.getRemoteAddress() + "] CoAP received [" + packet.toString(false) + "]");
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[" + packet.getRemoteAddress() + "] CoAP received [" + packet.toString(false, false, false, true) + "]");
        }

        if (handlePing(packet)) {
            return;
        }

        if (packet.getMethod() != null && packet.getMessageType() != MessageType.Acknowledgement) {
            handleRequest(packet, transportContext);
            return;
        } else {
            if (handleResponse(packet)) {
                return;
            } else if (handleDelayedResponse(packet)) {
                return;
            } else if (handleObservation(packet, transportContext)) {
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

    protected void sendResponse(CoapPacket request, CoapPacket response) {
        sendResponse(request, response, TransportContext.NULL);
    }


    private void handleNotProcessedMessage(CoapPacket packet) {
        CoapPacket resp = packet.createResponse(null);
        if (resp != null) {
            resp.setMessageType(MessageType.Reset);
            LOGGER.warn("Can not process CoAP message [{}] sent RESET message", packet);
            sendResponse(packet, resp);
        } else {
            handleNotProcessedMessageWeAreNotRespondingTo(packet);
        }
    }

    public void setConnectHandler(Consumer<InetSocketAddress> connectHandler) {
        this.connectedHandler = connectHandler;
    }

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        LOGGER.debug("[{}] Disconnected", remoteAddress);
    }

    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        if (connectedHandler != null) {
            connectedHandler.accept(remoteAddress);
        }
        LOGGER.debug("[{}] Connected", remoteAddress);
    }

    private static void handleNotProcessedMessageWeAreNotRespondingTo(CoapPacket packet) {
        if (MessageType.Acknowledgement.equals(packet.getMessageType())) {
            LOGGER.debug("Discarding extra ACK: {}", packet);
            return;
        }
        LOGGER.warn("Can not process CoAP message [{}]", packet);
    }

    public InetSocketAddress getLocalSocketAddress() {
        return coapTransporter.getLocalSocketAddress();
    }

    protected abstract boolean handleDelayedResponse(CoapPacket packet);

    protected abstract boolean handleResponse(CoapPacket packet);

    protected void handleRequest(CoapPacket packet, TransportContext transContext) {
        coapRequestHandler.handleRequest(packet, transContext);
    }

    protected boolean handleObservation(CoapPacket packet, TransportContext transContext) {
        return coapRequestHandler.handleObservation(packet, transContext);
    }

}
