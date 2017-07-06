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
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.internal.CoapExchangeImpl;
import com.mbed.coap.server.internal.CoapServerAbstract;
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.server.internal.CoapTransactionId;
import com.mbed.coap.server.internal.DelayedTransactionId;
import com.mbed.coap.server.internal.DelayedTransactionManager;
import com.mbed.coap.server.internal.DuplicationDetector;
import com.mbed.coap.server.internal.TransactionManager;
import com.mbed.coap.server.internal.UriMatcher;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.FutureCallbackAdapter;
import com.mbed.coap.utils.RequestCallback;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements CoAP server ( RFC 7252)
 *
 * @author szymon
 * @see <a href="http://www.rfc-editor.org/rfc/rfc7252.txt" >http://www.rfc-editor.org/rfc/rfc7252.txt</a>
 */
public abstract class CoapServer extends CoapServerAbstract implements Closeable {

    private final static long TRANSACTION_TIMEOUT_DELAY = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private static final int DEFAULT_DUPLICATION_TIMEOUT = 30000;
    private boolean isRunning;
    private final Map<UriMatcher, CoapHandler> handlers = new HashMap<>();
    private ScheduledExecutorService scheduledExecutor;
    private CoapTransport coapTransporter;
    private BlockSize blockOptionSize; //null: no blocking
    private boolean isSelfCreatedExecutor;
    private final TransactionManager transMgr = new TransactionManager();
    private final DelayedTransactionManager delayedTransMagr = new DelayedTransactionManager();
    private ObservationHandler observationHandler;
    private DuplicationDetector duplicationDetector;
    private MessageIdSupplier idContext;
    private boolean enabledCriticalOptTest = true;
    private ScheduledFuture<?> transactionTimeoutWorkerFut;
    private int maxIncomingBlockTransferSize;
    private CoapTransaction.Priority defaultPriority;


    public static CoapServerBuilder builder() {
        return new CoapServerBuilder();
    }

    final void init(int duplicationListSize, CoapTransport coapTransporter,
            ScheduledExecutorService scheduledExecutor, boolean isSelfCreatedExecutor,
            MessageIdSupplier idContext,
            int maxQueueSize, CoapTransaction.Priority defaultPriority,
            int maxIncomingBlockTransferSize,
            BlockSize blockSize, long delayedTransactionTimeout, DuplicatedCoapMessageCallback duplicatedCoapMessageCallback) {

        if (coapTransporter == null || scheduledExecutor == null || idContext == null || defaultPriority == null || duplicatedCoapMessageCallback == null) {
            throw new NullPointerException();
        }

        this.coapTransporter = coapTransporter;
        this.scheduledExecutor = scheduledExecutor;
        this.isSelfCreatedExecutor = isSelfCreatedExecutor;
        this.idContext = idContext;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.blockOptionSize = blockSize;
        this.delayedTransactionTimeout = delayedTransactionTimeout;
        this.duplicatedCoapMessageCallback = duplicatedCoapMessageCallback;

        this.defaultPriority = defaultPriority;
        transMgr.setMaximumEndpointQueueSize(maxQueueSize);

        if (transmissionTimeout == null) {
            this.transmissionTimeout = new CoapTimeout();
        }

        if (duplicationListSize > 0) {
            duplicationDetector = new DuplicationDetector(TimeUnit.MILLISECONDS, DEFAULT_DUPLICATION_TIMEOUT, duplicationListSize, scheduledExecutor);
        }
    }

    @Override
    protected DuplicationDetector getDuplicationDetector() {
        return duplicationDetector;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
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
        if (duplicationDetector != null) {
            duplicationDetector.start();
        }
        startTransactionTimeoutWorker();
        isRunning = true;
        return this;
    }

    private void assertNotRunning() {
        assume(!isRunning, "CoapServer is running");
    }

    private void startTransactionTimeoutWorker() {
        transactionTimeoutWorkerFut = scheduledExecutor.scheduleWithFixedDelay(this::resendTimeouts,
                0, TRANSACTION_TIMEOUT_DELAY, TimeUnit.MILLISECONDS);
    }

    private void stopTransactionTimeoutWorker() {
        if (transactionTimeoutWorkerFut != null) {
            transactionTimeoutWorkerFut.cancel(false);
            transactionTimeoutWorkerFut = null;
        }
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
        if (duplicationDetector != null) {
            duplicationDetector.stop();
        }

        LOGGER.trace("Stopping CoAP server..");
        stopTransactionTimeoutWorker();
        coapTransporter.stop();


        if (isSelfCreatedExecutor) {
            scheduledExecutor.shutdown();
        }
        LOGGER.debug("CoAP Server stopped");
    }

    @Override
    public void close() {
        this.stop();
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
     * Sets CoAP transmission timeout settings, use this to change default CoAP timeout
     *
     * @param transmissionTimeout transmission timeout
     */
    public void setTransmissionTimeout(TransmissionTimeout transmissionTimeout) {
        this.transmissionTimeout = transmissionTimeout;
    }

    /**
     * Returns next CoAP message id
     *
     * @return message id
     */
    @Override
    public int getNextMID() {
        return idContext.getNextMID();
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
     * Returns defines block size
     *
     * @return block size
     */
    public final BlockSize getBlockSize() {
        return blockOptionSize;
    }

    final int getMaxIncomingBlockTransferSize() {
        return this.maxIncomingBlockTransferSize;
    }

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
    public void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext) {
        makeRequestInternal(packet, callback, transContext, defaultPriority);
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
     * @param transactionPriority defines transaction priority (used by CoapServerBlocks mostyl)
     */
    private void makeRequestInternal(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext, CoapTransaction.Priority transactionPriority) {
        makeRequestInternal(packet, callback, transContext, transactionPriority, false);
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
     * @param coapCallback handles response
     * @param transContext transport context that will be passed to transport connector
     * @param transactionPriority defines transaction priority (used by CoapServerBlocks mostyl)
     * @param forceAddToQueue forces add to queue even if there is queue limit overflow (block requests)
     */
    void makeRequestInternal(final CoapPacket packet, final Callback<CoapPacket> coapCallback, final TransportContext transContext, CoapTransaction.Priority transactionPriority, boolean forceAddToQueue) {
        if (packet == null || packet.getRemoteAddress() == null) {
            throw new NullPointerException();
        }
        RequestCallback requestCallback = wrapCallback(coapCallback);

        //assign new MID
        packet.setMessageId(getNextMID());

        if (packet.getMustAcknowledge()) {
            CoapTransaction trans = new CoapTransaction(requestCallback, packet, this, transContext, transactionPriority, this::removeCoapTransId);
            try {
                if (transMgr.addTransactionAndGetReadyToSend(trans, forceAddToQueue)) {
                    trans.send();
                }
            } catch (TooManyRequestsForEndpointException e) {
                coapCallback.callException(e);
            }
        } else {
            //send NON message without waiting for piggy-backed response
            DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
            delayedTransMagr.add(delayedTransactionId, new CoapTransaction(requestCallback, packet, this, transContext, transactionPriority, this::removeCoapTransId));
            this.send(packet, packet.getRemoteAddress(), transContext)
                    .whenComplete((wasSent, maybeError) -> {
                        if (maybeError == null) {
                            requestCallback.onSent();
                        } else {
                            delayedTransMagr.remove(delayedTransactionId);
                            requestCallback.callException(((Exception) maybeError));
                        }
                    });
            if (packet.getToken().length == 0) {
                LOGGER.warn("Sent NON request without token: {}", packet);
            }
        }

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

    @Override
    protected CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
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

    /**
     * Returns number of waiting transaction.
     *
     * @return number of transactions
     */
    public int getNumberOfTransactions() {
        return transMgr.getNumberOfTransactions();
    }

    /**
     * Handles incoming messages
     */
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

    private boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null && packet.getMessageType() == MessageType.Confirmable) {
            LOGGER.debug("CoAP ping received.");
            CoapPacket resp = packet.createResponse(null);
            resp.setMessageType(MessageType.Reset);
            sendResponseAndUpdateDuplicateDetector(packet, resp);
            return true;
        }
        return false;
    }

    private boolean handleObservation(CoapPacket packet, TransportContext context) {
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

    private boolean handleDelayedResponse(CoapPacket packet) {
        DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CoapTransaction trans = delayedTransMagr.find(delayedTransactionId);

        if (trans != null) {
            delayedTransMagr.remove(delayedTransactionId);
            if (packet.getMustAcknowledge()) {
                CoapPacket resp = packet.createResponse();
                sendResponseAndUpdateDuplicateDetector(packet, resp);
            }

            trans.invokeCallback(packet);
            return true;
        }
        return false;
    }

    private void removeCoapTransId(CoapTransactionId coapTransId) {
        transMgr.unlockOrRemoveAndGetNext(coapTransId)
                .ifPresent(CoapTransaction::send);
    }

    private void invokeCallbackAndRemoveTransaction(CoapTransaction transaction, CoapPacket packet) {
        // first call callback and only then remove transaction - important for CoapServerBlocks
        // in other way block transfer will be interrupted by other messages in the queue
        // of TransactionManager, because removeCoapTransId() also sends next message form the queue

        transaction.invokeCallback(packet);
        removeCoapTransId(transaction.getTransactionId());
    }

    private boolean handleResponse(CoapPacket packet) {
        //find corresponding transaction
        CoapTransactionId coapTransId = new CoapTransactionId(packet);

        Optional<CoapTransaction> maybeTrans = transMgr.removeAndLock(coapTransId);
        if (!maybeTrans.isPresent() && packet.getMessageType() == MessageType.Confirmable || packet.getMessageType() == MessageType.NonConfirmable) {
            //find if it is separate response
            maybeTrans = transMgr.findMatchAndRemoveForSeparateResponse(packet);
        }

        return maybeTrans
                .map(trans -> handleResponse(trans, packet))
                .orElse(false);
    }

    private boolean handleResponse(CoapTransaction trans, CoapPacket packet) {
        MessageType messageType = packet.getMessageType();
        if (packet.getCode() != null || messageType == MessageType.Reset) {
            invokeCallbackAndRemoveTransaction(trans, packet);
            return true;
        }

        assume(messageType == MessageType.Acknowledgement, "not handled transaction");

        if (trans.getCoapRequest().getMethod() == null) {
            invokeCallbackAndRemoveTransaction(trans, packet);
            return true;
        }

        //delayed response
        DelayedTransactionId delayedTransactionId = new DelayedTransactionId(trans.getCoapRequest().getToken(), packet.getRemoteAddress());
        removeCoapTransId(trans.getTransactionId());
        delayedTransMagr.add(delayedTransactionId, trans);
        return true;

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

    private boolean findDuplicate(CoapPacket request, String message) {
        //request
        if (duplicationDetector != null) {
            CoapPacket duplResp = duplicationDetector.isMessageRepeated(request);
            if (duplResp != null) {
                if (duplResp != DuplicationDetector.EMPTY_COAP_PACKET) {
                    sendPacket(duplResp, request.getRemoteAddress(), TransportContext.NULL);
                    LOGGER.debug("{}, resending response [{}]", message, request);
                } else {
                    LOGGER.debug("{}, no response available [{}]", message, request);
                }

                duplicatedCoapMessageCallback.duplicated(request);

                return true;
            }
        }
        return false;
    }

    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext transportContext) throws CoapException {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transportContext);
        coapHandler.handle(exchange);
    }

    void resendTimeouts() {
        try {
            //find timeouts
            final long currentTime = System.currentTimeMillis();
            Collection<CoapTransaction> transTimeOut = transMgr.findTimeoutTransactions(currentTime);
            for (CoapTransaction trans : transTimeOut) {
                if (trans.isTimedOut(currentTime)) {
                    LOGGER.trace("resendTimeouts: try to resend timed out transaction [{}]", trans);
                    if (!trans.send(currentTime)) {
                        //final timeout, cannot resend, remove transaction
                        removeCoapTransId(trans.getTransactionId());
                        LOGGER.trace("resendTimeouts: CoAP transaction final timeout [{}]", trans);
                        trans.getCallback().callException(new CoapTimeoutException(trans));

                    } else {
                        if (trans.getCallback() instanceof CoapTransactionCallback) {
                            ((CoapTransactionCallback) trans.getCallback()).messageResent();
                        }
                    }
                }
            }

            Collection<CoapTransaction> delayedTransTimeOut = delayedTransMagr.findTimeoutTransactions(currentTime);
            for (CoapTransaction trans : delayedTransTimeOut) {
                if (trans.isTimedOut(currentTime)) {
                    //delayed timeout, remove transaction
                    delayedTransMagr.remove(trans.getDelayedTransId());
                    LOGGER.trace("CoAP delayed transaction timeout [{}]", trans.getDelayedTransId());
                    trans.getCallback().callException(new CoapTimeoutException(trans));
                }
            }
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
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
    public abstract byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext);

    public abstract byte[] observe(CoapPacket request, final Callback<CoapPacket> respCallback, TransportContext transportContext);

    private static void assume(boolean assumeCondition, String errorMessage) {
        if (!assumeCondition) {
            throw new IllegalStateException(errorMessage);
        }
    }

    static RequestCallback wrapCallback(Callback<CoapPacket> callback) {
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
