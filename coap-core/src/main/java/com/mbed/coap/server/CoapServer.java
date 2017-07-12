/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.CoapConstants;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.internal.CoapExchangeImpl;
import com.mbed.coap.server.internal.ResourceLinks;
import com.mbed.coap.server.internal.UriMatcher;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
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
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CoapServer implements CoapReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    private final Map<UriMatcher, CoapHandler> handlers = new HashMap<>();
    private boolean enabledCriticalOptTest = true;
    protected ObservationHandler observationHandler;
    protected CoapTransport coapTransporter;
    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();

    public static CoapServerBuilder builder() {
        return new CoapServerBuilder();
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
        coapTransporter.start(this);
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
        stop0();
        coapTransporter.stop();

        LOGGER.debug("CoAP Server stopped");
    }

    protected abstract void stop0();

    /**
     * Informs if server is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }


    /**
     * Returns next CoAP message id
     *
     * @return message id
     */
    protected abstract int getNextMID();

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
     * Returns defines block size (per endpoint if applicable)
     *
     * @return block size per endpoint (if applicable) or generic setting for server or null if block transfers are not supported
     */
    abstract public BlockSize getBlockSize(InetSocketAddress remoteAddress);

    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return coapTransporter.getLocalSocketAddress();
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
    abstract public void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext);


    /**
     * Sets handler for receiving notifications.
     *
     * @param observationHandler observation handler
     */
    public void setObservationHandler(ObservationHandler observationHandler) {
        this.observationHandler = observationHandler;
        LOGGER.trace("Observation handler set [{}]", observationHandler);
    }

    protected final CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        return coapTransporter
                .sendPacket(coapPacket, adr, tranContext)
                .whenComplete((__, throwable) -> logCoapSent(coapPacket, throwable));
    }

    private void logCoapSent(CoapPacket coapPacket, Throwable maybeError) {
        if (maybeError != null) {
            LOGGER.warn("[{}] Failed to sent: {}", coapPacket.getRemoteAddress(), maybeError.toString());
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("CoAP sent [" + coapPacket.toString(true) + "]");
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("CoAP sent [" + coapPacket.toString(false) + "]");
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[" + coapPacket.getRemoteAddress() + "] CoAP sent [" + coapPacket.toString(false, false, false, true) + "]");
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

        if (packet.getMethod() != null) {
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
            sendResponseAndUpdateDuplicateDetector(packet, resp);
            return true;
        }
        return false;
    }

    protected boolean handleObservation(CoapPacket packet, TransportContext context) {
        ObservationHandler obsHdlr = this.observationHandler;
        if (obsHdlr == null) {
            return false;
        }

        Integer observe = packet.headers().getObserve();
        if (observe != null || obsHdlr.hasObservation(packet.getToken())) {
            if (observe == null || observe == 1 || (packet.getCode() != Code.C205_CONTENT && packet.getCode() != Code.C203_VALID)) {

                LOGGER.trace("Notification termination [{}]", packet);

                if (packet.getMustAcknowledge()) {
                    CoapPacket response = packet.createResponse();
                    sendResponseAndUpdateDuplicateDetector(packet, response);
                }

                obsHdlr.callException(new ObservationTerminatedException(packet, context));
                return true;
            }

            if (!findDuplicate(packet, "CoAP notification repeated")) {
                LOGGER.trace("Notification [{}]", packet.getRemoteAddress());
                CoapExchange exchange = new CoapExchangeImpl(packet, this, context);
                obsHdlr.call(exchange);
            }
            return true;
        }
        return false;
    }

    private void handleNotProcessedMessage(CoapPacket packet) {
        CoapPacket resp = packet.createResponse(null);
        if (resp != null) {
            resp.setMessageType(MessageType.Reset);
            if (packet.getMessageType() == MessageType.NonConfirmable) {
                resp.setMessageId(getNextMID());
            }
            LOGGER.warn("Can not process CoAP message [{}] sent RESET message", packet);
            sendResponseAndUpdateDuplicateDetector(packet, resp);
        } else {
            handleNotProcessedMessageWeAreNotRespondingTo(packet);
        }
    }

    private static void handleNotProcessedMessageWeAreNotRespondingTo(CoapPacket packet) {
        if (MessageType.Acknowledgement.equals(packet.getMessageType())) {
            LOGGER.debug("Discarding extra ACK: {}", packet);
            return;
        }
        LOGGER.warn("Can not process CoAP message [{}]", packet);
    }

    protected abstract boolean handleDelayedResponse(CoapPacket packet);

    protected abstract boolean handleResponse(CoapPacket packet);

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

    private void handleRequest(CoapPacket request, TransportContext transportContext) {
        if (findDuplicate(request, "CoAP request repeated")) {
            return;
        }
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
            sendResponseAndUpdateDuplicateDetector(request, errorResponse);
        }
    }

    protected abstract boolean findDuplicate(CoapPacket request, String message);

    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext transportContext) throws CoapException {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transportContext);
        coapHandler.handle(exchange);
    }



    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp) {
        sendResponseAndUpdateDuplicateDetector(request, resp, TransportContext.NULL);
    }

    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp, TransportContext ctx) {
        sendPacket(resp, request.getRemoteAddress(), ctx);
    }

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
        sendResponseAndUpdateDuplicateDetector(exchange.getRequest(), resp, exchange.getResponseTransportContext());
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
                if (resp.getCode() == Code.C205_CONTENT && resp.headers().getObserve() == null) {
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

    protected static void assume(boolean assumeCondition, String errorMessage) {
        if (!assumeCondition) {
            throw new IllegalStateException(errorMessage);
        }
    }

    protected static RequestCallback wrapCallback(Callback<CoapPacket> callback) {
        if (callback == null) {
            throw new NullPointerException();
        }
        if (callback instanceof RequestCallback) {
            return ((RequestCallback) callback);
        }

        return new InternalRequestCallback(callback);
    }

    static class InternalRequestCallback implements RequestCallback {
        private final Callback<CoapPacket> callback;

        InternalRequestCallback(Callback<CoapPacket> callback) {
            this.callback = callback;
        }

        @Override
        public void call(CoapPacket packet) {
            callback.call(packet);
        }

        @Override
        public void callException(Exception ex) {
            callback.callException(ex);
        }

        @Override
        public void onSent() {
            //ignore
        }
    }
}
