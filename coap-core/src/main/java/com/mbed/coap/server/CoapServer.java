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
package com.mbed.coap.server;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import static java.util.Objects.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapUnknownOptionException;
import com.mbed.coap.observe.ObservationHandler;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.server.internal.CoapRequestHandler;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    private final Service<CoapRequest, CoapResponse> requestHandlingService;
    private boolean enabledCriticalOptTest = true;
    final ObservationHandler observationHandler = new ObservationHandler();
    final CoapRequestHandler coapRequestHandler = new CoapRequestHandlerImpl();
    private final CoapMessaging coapMessaging;
    private final Service<CoapRequest, CoapResponse> clientService;

    public CoapServer(CoapMessaging coapMessaging, Filter.SimpleFilter<CoapRequest, CoapResponse> sendFilter, Service<CoapRequest, CoapResponse> routeService) {
        this.coapMessaging = coapMessaging;
        this.requestHandlingService = new ObservationSenderFilter(this::sendNotification)
                .then(requireNonNull(routeService));

        this.clientService = new ObserveRequestFilter(observationHandler)
                .andThen(sendFilter)
                .then(coapMessaging::send);
    }

    public static CoapServerBuilder.CoapServerBuilderForUdp builder() {
        return CoapServerBuilder.newBuilder();
    }

    /**
     * Starts CoAP server
     *
     * @return this instance
     * @throws IOException           exception from transport initialization
     * @throws IllegalStateException if server is already running
     */
    public synchronized CoapServer start() throws IOException, IllegalStateException {
        assertNotRunning();
        coapMessaging.start(coapRequestHandler);
        isRunning = true;
        return this;
    }

    private void assertNotRunning() {
        assume(!isRunning, "CoapServer is running");
    }

    /**
     * Stops CoAP server
     *
     * @throws IllegalStateException if server is already stopped
     */
    public final synchronized void stop() throws IllegalStateException {
        if (!isRunning) {
            throw new IllegalStateException("CoapServer is not running");
        }

        isRunning = false;
        LOGGER.trace("Stopping CoAP server..");
        coapMessaging.stop();

        LOGGER.debug("CoAP Server stopped");
    }

    /**
     * Informs if server is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }


    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return coapMessaging.getLocalSocketAddress();
    }


    public final Service<CoapRequest, CoapResponse> clientService() {
        return clientService;
    }

    public CompletableFuture<CoapPacket> sendNotification(final CoapPacket notifPacket, final TransportContext transContext) {
        if (notifPacket.headers().getObserve() == null) {
            throw new IllegalArgumentException("Notification packet should have observation header set");
        }
        if (notifPacket.getToken().isEmpty()) {
            throw new IllegalArgumentException("Notification packet should have non-empty token");
        }

        return coapMessaging.makeRequest(notifPacket, transContext);
    }

    public void setConnectHandler(Consumer<InetSocketAddress> disconnectConsumer) {
        coapMessaging.setConnectHandler(disconnectConsumer);
    }

    private class CoapRequestHandlerImpl implements CoapRequestHandler {

        @Override
        public boolean handleObservation(CoapPacket packet, TransportContext context) {
            Integer observe = packet.headers().getObserve();
            if (observe == null && !observationHandler.hasObservation(packet.getToken())) {
                return false;
            }
            CoapResponse obsResponse = packet.toCoapResponse();
            if (observe == null || (obsResponse.getCode() != Code.C205_CONTENT && obsResponse.getCode() != Code.C203_VALID)) {

                LOGGER.trace("Notification termination [{}]", packet);

                if (packet.getMustAcknowledge()) {
                    CoapPacket response = packet.createResponse();
                    sendResponse(packet, response);
                }

                observationHandler.terminate(packet.getToken(), obsResponse);
                return true;
            }

            LOGGER.trace("Notification [{}]", packet.getRemoteAddrString());
            boolean sendAck = observationHandler.notify(packet.getRemoteAddress(), packet.getToken(), obsResponse, CoapServer.this.clientService);
            CoapPacket response = packet.createResponse();
            if (!sendAck) {
                response.setMessageType(MessageType.Reset);
            }
            sendResponse(packet, response);
            return true;
        }

        @Override
        public void handleRequest(CoapPacket request, TransportContext transportContext) {
            if (enabledCriticalOptTest) {
                try {
                    request.headers().criticalOptTest();
                } catch (CoapUnknownOptionException ex) {
                    CoapPacket errorResponse = request.createResponse(ex.getCode());
                    errorResponse.setPayload(ex.getMessage());
                    sendResponse(request, errorResponse);
                    return;
                }
            }

            requestHandlingService.apply(request.toCoapRequest(transportContext))
                    .exceptionally(this::rescue)
                    .thenAccept(resp ->
                            coapMessaging.sendResponse(request, request.createResponseFrom(resp), transportContext)
                    );
        }

        private CoapResponse rescue(Throwable ex) {
            if (ex instanceof CompletionException) {
                return rescue(ex.getCause());
            }
            if (ex instanceof CoapCodeException) {
                return ((CoapCodeException) ex).toResponse();
            }

            LOGGER.warn("Unexpected exception: " + ex.getMessage(), ex);
            return CoapResponse.of(Code.C500_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Enable or disable test for critical options. If enabled and incoming coap packet contains non-recognized critical
     * option, server will send error message (4.02 bad option)
     *
     * @param enable if true then critical option verification is enabled
     */
    public void useCriticalOptionTest(boolean enable) {
        this.enabledCriticalOptTest = enable;
    }


    private void sendResponse(CoapPacket request, CoapPacket response) {
        coapMessaging.sendResponse(request, response, TransportContext.EMPTY);
    }


    public CoapMessaging getCoapMessaging() {
        return coapMessaging;
    }

}
