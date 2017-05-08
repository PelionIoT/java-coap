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
package com.mbed.coap.server;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.internal.CoapExchangeImpl;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.server.internal.CoapRequestHandler;
import com.mbed.coap.server.internal.ResourceLinks;
import com.mbed.coap.server.internal.UriMatcher;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.FutureCallbackAdapter;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    private final Map<UriMatcher, CoapHandler> handlers = new HashMap<>();
    private boolean enabledCriticalOptTest = true;
    protected ObservationHandler observationHandler;
    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();
    final CoapRequestHandler coapRequestHandler = new CoapRequestHandlerImpl();

    private final CoapMessaging coapMessaging;

    public CoapServer(CoapMessaging coapMessaging) {
        this.coapMessaging = coapMessaging;
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
     * Adds handler for incoming requests. URI context can be absolute or with postfix. Postfix can be a star sign (*)
     * for example: /s/temp*, it means that all request under /s/temp/ will be directed to a given handler.
     *
     * @param uri URI of a resource
     * @param coapHandler Handler object
     */
    public void addRequestHandler(String uri, CoapHandler coapHandler) {
        handlers.put(new UriMatcher(uri), coapHandler);
        LOGGER.debug("Handler added on {}", uri);
    }

    /**
     * Removes request handler from server
     *
     * @param requestHandler request handler
     */
    public void removeRequestHandler(CoapHandler requestHandler) {
        UriMatcher url = findKey(requestHandler);
        handlers.remove(url);
    }

    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return coapMessaging.getLocalSocketAddress();
    }

    private UriMatcher findKey(CoapHandler requestHandler) {
        for (Entry<UriMatcher, CoapHandler> entry : handlers.entrySet()) {
            if (entry.getValue() == requestHandler) {
                return entry.getKey();
            }
        }
        return null;
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
    public final CompletableFuture<CoapPacket> makeRequest(CoapPacket requestPacket, final TransportContext transContext) {
        FutureCallbackAdapter<CoapPacket> completableFuture = new FutureCallbackAdapter<>();
        makeRequest(requestPacket, completableFuture, transContext);

        return completableFuture;
    }

    /**
     * Makes CoAP request. Sends given packet to specified address. Reply is called through asynchronous Callback
     * interface.
     * <p>
     * <i>Asynchronous method</i>
     * </p>
     * NOTE: If exception is thrown then callback will never be invoked.
     *
     * @param packet request packet
     * @param callback handles response
     */
    public final void makeRequest(CoapPacket packet, Callback<CoapPacket> callback) {
        makeRequest(packet, callback, TransportContext.NULL);
    }

    /**
     * Makes CoAP request. Sends given packet to specified address. Reply is called through asynchronous Callback
     * interface.
     * <p>
     * <i>Asynchronous method</i>
     * </p>
     * NOTE: If exception is thrown then callback will never be invoked.
     *
     * @param packet request packet
     * @param callback handles response
     * @param transContext transport context that will be passed to transport connector
     */
    public void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext) {
        coapMessaging.makeRequest(packet, callback, transContext);
    }

    public void sendNotification(final CoapPacket notifPacket, final Callback<CoapPacket> callback, final TransportContext transContext) {
        if (notifPacket.headers().getObserve() == null) {
            throw new IllegalArgumentException("Notification packet should have observation header set");
        }
        if (notifPacket.getToken() == null || notifPacket.getToken().length == 0) {
            throw new IllegalArgumentException("Notification packet should have non-empty token");
        }
        if (notifPacket.getCode() != Code.C205_CONTENT) {
            throw new IllegalArgumentException("Notification packet should have 205_CONTENT code");
        }

        makeRequest(notifPacket, callback, transContext);
    }

    public void ping(InetSocketAddress destination, final Callback<CoapPacket> callback) {
        coapMessaging.ping(destination, callback);
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
            LOGGER.trace("Notification [{}]", packet.getRemoteAddress());
            CoapExchange exchange = new CoapExchangeImpl(packet, CoapServer.this, context);
            obsHdlr.call(exchange);
            //            }
            return true;
        }

        @Override
        public void handleRequest(CoapPacket request, TransportContext transportContext) {
            String uri = request.headers().getUriPath();
            if (uri == null) {
                uri = "/";
            }
            CoapPacket errorResponse = null;

            CoapHandler coapHandler = findHandler(uri);

            if (coapHandler != null) {
                try {
                    if (enabledCriticalOptTest) {
                        request.headers().criticalOptTest();
                    }
                    callRequestHandler(request, coapHandler, transportContext);
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
            } else {
                errorResponse = request.createResponse(Code.C404_NOT_FOUND);
            }
            if (errorResponse != null) {
                sendResponse(request, errorResponse);
            }
        }
    }

    private CoapHandler findHandler(String uri) {

        CoapHandler handler = handlers.get(new UriMatcher(uri));

        if (handler == null) {
            for (Entry<UriMatcher, CoapHandler> entry : handlers.entrySet()) {
                if (entry.getKey().isMatching(uri)) {
                    return entry.getValue();
                }
            }
        }
        return handler;
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

    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext transportContext) throws CoapException {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transportContext);
        coapHandler.handle(exchange);
    }

    protected void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext) {
        coapMessaging.sendResponse(request, response, transContext);
    }

    protected void sendResponse(CoapPacket request, CoapPacket response) {
        sendResponse(request, response, TransportContext.NULL);
    }

    //    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp) {
    //        sendResponseAndUpdateDuplicateDetector(request, resp, TransportContext.NULL);
    //    }
    //
    //    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp, TransportContext ctx) {
    //        coapMessaging.sendPacket(resp, request.getRemoteAddress(), ctx);
    //    }

    /**
     * Returns handler that can be used as /.well-known/core resource.
     *
     * @return CoapHandler instance
     */
    public CoapHandler getResourceLinkResource() {
        return new ResourceLinks(this);
    }

    /**
     * Returns list of links, assigned from attached resource handlers.
     *
     * @return list with LinkFormat
     */
    public List<LinkFormat> getResourceLinks() {
        List<LinkFormat> linkFormats = new LinkedList<>();
        //List<LinkFormat> linkFormats = new
        for (Entry<UriMatcher, CoapHandler> entry : handlers.entrySet()) {
            UriMatcher uri = entry.getKey();
            if (uri.getUri().equals(CoapConstants.WELL_KNOWN_CORE)) {
                continue;
            }
            CoapHandler handler = handlers.get(uri);

            LinkFormat lf;
            if (handler instanceof CoapResource) {
                CoapResource coapRes;
                coapRes = (CoapResource) handler;
                lf = coapRes.getLink();
                lf.setUri(uri.getUri());
                //                lf.setContentType(coapRes.getType());
                //                if (coapRes != null && coapRes instanceof CoapObservableResource) {
                //                    lf.setObservable(true);
                //                }
                //                lf.put("title", coapRes.getName());
            } else {
                lf = new LinkFormat(uri.getUri());
            }

            //if (lf != null && ((resourceTypeFilter == null && lf.getResourceType() == null) || (Arrays.binarySearch(lf.getResourceType(), resourceTypeFilter) >= 0))) {
            linkFormats.add(lf);
            //}
        }
        return linkFormats;
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
     * @param respCallback handles observation response
     * @param token observation identification (token)
     * @return observation identification
     */
    public byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) {
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, uri, destination);
        request.setToken(token);
        request.headers().setObserve(0);
        return observe(request, respCallback, transportContext);
    }

    public byte[] observe(CoapPacket request, final Callback<CoapPacket> respCallback, TransportContext transportContext) {
        if (request.headers().getObserve() == null) {
            request.headers().setObserve(0);
        }
        if (request.getToken() == CoapPacket.DEFAULT_TOKEN) {
            request.setToken(observationIDGenerator.nextObservationID(request.headers().getUriPath()));
        }
        makeRequest(request, new RequestCallback() {

            @Override
            public void onSent() {
                if (respCallback instanceof RequestCallback) {
                    ((RequestCallback) respCallback).onSent();
                }
            }

            @Override
            public void callException(Exception ex) {
                respCallback.callException(ex);
            }

            @Override
            public void call(CoapPacket resp) {
                if (resp.getCode() != Code.C205_CONTENT || resp.headers().getObserve() == null) {
                    respCallback.callException(new ObservationNotEstablishedException(resp));
                    return;
                }
                respCallback.call(resp);
            }
        }, transportContext);
        return request.getToken();
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
