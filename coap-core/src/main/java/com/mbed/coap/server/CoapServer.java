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
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.Objects.*;
import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.internal.CoapExchangeImpl;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.server.internal.CoapRequestHandler;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.FutureCallbackAdapter;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    protected final CoapHandlerToServiceAdapter handler;
    private boolean enabledCriticalOptTest = true;
    protected ObservationHandler observationHandler;
    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();
    final CoapRequestHandler coapRequestHandler = new CoapRequestHandlerImpl();

    private final CoapMessaging coapMessaging;

    public CoapServer(CoapMessaging coapMessaging, Service<CoapRequest, CoapResponse> routeService) {
        this.coapMessaging = coapMessaging;
        this.handler = new CoapHandlerToServiceAdapter(
                new ObservationSenderFilter(this::sendNotification).then(requireNonNull(routeService))
        );
    }

    public static CoapServerBuilder.CoapServerBuilderForUdp builder() {
        return CoapServerBuilder.newBuilder();
    }

    public static CoapServerBuilder.CoapServerBuilderForTcp builderForTcp() {
        return CoapServerBuilder.newBuilderForTcp();
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


    /**
     * Makes asynchronous CoAP request. Sends given packet to specified address..
     *
     * @param requestPacket request packet
     * @return CompletableFuture with response promise
     */
    public final CompletableFuture<CoapPacket> makeRequest(CoapPacket requestPacket) {
        return makeRequest(requestPacket, TransportContext.NULL);
    }

    /**
     * Makes asynchronous CoAP request. Sends given packet to specified address..
     *
     * @param requestPacket request packet
     * @param transContext transport context that will be passed to transport connector
     * @return CompletableFuture with response promise
     */
    public CompletableFuture<CoapPacket> makeRequest(CoapPacket requestPacket, final TransportContext transContext) {
        FutureCallbackAdapter<CoapPacket> completableFuture = new FutureCallbackAdapter<>();
        coapMessaging.makeRequest(requestPacket, completableFuture, transContext);

        return completableFuture;
    }

    public CompletableFuture<CoapPacket> sendNotification(final CoapPacket notifPacket, final TransportContext transContext) {
        if (notifPacket.headers().getObserve() == null) {
            throw new IllegalArgumentException("Notification packet should have observation header set");
        }
        if (notifPacket.getToken().isEmpty()) {
            throw new IllegalArgumentException("Notification packet should have non-empty token");
        }

        return makeRequest(notifPacket, transContext);
    }

    public CompletableFuture<CoapPacket> ping(InetSocketAddress destination) {
        FutureCallbackAdapter<CoapPacket> callbackAdapter = new FutureCallbackAdapter<>();

        coapMessaging.ping(destination, callbackAdapter);
        return callbackAdapter;
    }

    /**
     * Sets handler for receiving notifications.
     *
     * @param observationHandler observation handler
     */
    public void setObservationHandler(ObservationHandler observationHandler) {
        this.observationHandler = observationHandler;
        LOGGER.trace("Observation handler set [{}]", observationHandler);
    }

    public void setConnectHandler(Consumer<InetSocketAddress> disconnectConsumer) {
        coapMessaging.setConnectHandler(disconnectConsumer);
    }

    private class CoapRequestHandlerImpl implements CoapRequestHandler {

        @Override
        public boolean handleObservation(CoapPacket packet, TransportContext context) {
            ObservationHandler obsHdlr = observationHandler;
            if (obsHdlr == null) {
                return false;
            }

            Integer observe = packet.headers().getObserve();
            if (observe == null && !obsHdlr.hasObservation(packet.getToken())) {
                return false;
            }

            if (observe == null || (packet.getCode() != Code.C205_CONTENT && packet.getCode() != Code.C203_VALID)) {

                LOGGER.trace("Notification termination [{}]", packet);

                if (packet.getMustAcknowledge()) {
                    CoapPacket response = packet.createResponse();
                    sendResponse(packet, response);
                }

                obsHdlr.callException(new ObservationTerminatedException(packet, context));
                return true;
            }

            //            if (!findDuplicate(packet, "CoAP notification repeated")) {
            LOGGER.trace("Notification [{}]", packet.getRemoteAddrString());
            CoapExchange exchange = new CoapExchangeImpl(packet, CoapServer.this, context);
            obsHdlr.call(exchange);
            //            }
            return true;
        }

        @Override
        public void handleRequest(CoapPacket request, TransportContext transportContext) {
            CoapPacket errorResponse = null;

            try {
                if (enabledCriticalOptTest) {
                    request.headers().criticalOptTest();
                }
                callRequestHandler(request, transportContext);
            } catch (CoapRequestEntityTooLarge ex) {
                errorResponse = request.createResponse(ex.getCode());
                if (ex.getMaxSize() > 0) {
                    errorResponse.headers().setSize1(ex.getMaxSize());
                }
                if (ex.getBlockOptionHint() != null) {
                    errorResponse.headers().setBlock1Req(ex.getBlockOptionHint());
                }
                errorResponse.setPayload(ex.getMessage());
            } catch (CoapCodeException ex) {
                errorResponse = request.createResponse(ex.getCode());
                errorResponse.setPayload(ex.getMessage());
            } catch (Exception ex) {
                LOGGER.warn("Unexpected exception: " + ex.getMessage(), ex);
                errorResponse = request.createResponse(Code.C500_INTERNAL_SERVER_ERROR);
            }
            if (errorResponse != null) {
                sendResponse(request, errorResponse);
            }
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


    //    protected abstract boolean findDuplicate(CoapPacket request, String message);

    protected void callRequestHandler(CoapPacket request, TransportContext transportContext) throws CoapException {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transportContext);
        handler.handle(exchange);
    }

    protected void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext) {
        coapMessaging.sendResponse(request, response, transContext);
    }

    protected void sendResponse(CoapPacket request, CoapPacket response) {
        sendResponse(request, response, TransportContext.NULL);
    }


    public void sendResponse(CoapExchange exchange) {
        CoapPacket resp = exchange.getResponse();
        if (resp == null) {
            //nothing to send
            return;
        }
        sendResponse(exchange.getRequest(), resp, exchange.getResponseTransportContext());
    }


    /**
     * Initialize observation.
     *
     * <p>
     * <i>Asynchronous method</i>
     * </p>
     *
     * @param uri resource path for observation
     * @param destination destination address
     * @param token observation identification (token)
     * @return response
     */
    public CompletableFuture<CoapPacket> observe(String uri, InetSocketAddress destination, Opaque token, TransportContext transportContext) {
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, uri, destination);
        request.setToken(token);
        request.headers().setObserve(0);
        return observe(request, transportContext);
    }

    public CompletableFuture<CoapPacket> observe(CoapPacket request, TransportContext transportContext) {
        if (request.headers().getObserve() == null) {
            request.headers().setObserve(0);
        }
        if (request.getToken().isEmpty()) {
            request.setToken(observationIDGenerator.nextObservationID(request.headers().getUriPath()));
        }

        return makeRequest(request, transportContext)
                .thenCompose(resp -> {
                    if (resp.getCode() != Code.C205_CONTENT || resp.headers().getObserve() == null) {
                        return failedFuture(new ObservationNotEstablishedException(resp));
                    } else {
                        return completedFuture(resp);
                    }
                });
    }

    /**
     * Sets observation id generator instance.
     *
     * @param observationIDGenerator observation id generator instance
     */
    public void setObservationIDGenerator(ObservationIDGenerator observationIDGenerator) {
        this.observationIDGenerator = observationIDGenerator;
    }

    public CoapMessaging getCoapMessaging() {
        return coapMessaging;
    }
}
