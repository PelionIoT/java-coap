/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.exception.TooManyRequestsForEndpointException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.internal.CoapExchangeImpl;
import org.mbed.coap.server.internal.CoapServerAbstract;
import org.mbed.coap.server.internal.CoapTransaction;
import org.mbed.coap.server.internal.CoapTransactionId;
import org.mbed.coap.server.internal.DelayedTransactionId;
import org.mbed.coap.server.internal.DelayedTransactionManager;
import org.mbed.coap.server.internal.DuplicationDetector;
import org.mbed.coap.server.internal.TransactionManager;
import org.mbed.coap.server.internal.UriMatcher;
import org.mbed.coap.transmission.CoapTimeout;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.EventLogger;
import org.mbed.coap.utils.EventLoggerCoapPacket;
import org.mbed.coap.utils.FutureCallbackAdapter;

/**
 * Implements CoAP server ( RFC 7252)
 *
 * @author szymon
 * @see <a href="http://www.rfc-editor.org/rfc/rfc7252.txt" >http://www.rfc-editor.org/rfc/rfc7252.txt</a>
 */
public abstract class CoapServer extends CoapServerAbstract implements Closeable {

    private final static long TRANSACTION_TIMEOUT_DELAY = 1000;
    private static final Logger LOGGER = Logger.getLogger(CoapServer.class.getName());
    private static final EventLogger EVENT_LOGGER = EventLogger.getLogger("coap");
    private static final int DEFAULT_MAX_DUPLICATION_LIST_SIZE = 10000;
    private static final int DEFAULT_DUPLICATION_TIMEOUT = 30000;
    private boolean isRunning;
    private final Map<UriMatcher, CoapHandler> handlers = new HashMap<>();
    private ScheduledExecutorService scheduledExecutor;
    private TransportConnector transport;
    private BlockSize blockOptionSize; //null: no blocking
    private boolean isSelfCreatedExecutor;
    protected TransactionManager transMgr = new TransactionManager();
    private final DelayedTransactionManager delayedTransMagr = new DelayedTransactionManager();
    private ObservationHandler observationHandler;
    private DuplicationDetector duplicationDetector;
    private MessageIdSupplier idContext;
    private boolean enabledCriticalOptTest = true;
    private ScheduledFuture<?> transactionTimeoutWorkerFut;
    private int maxIncomingBlockTransferSize;
    private CoapTransaction.Priority defaultPriority = CoapTransaction.Priority.NORMAL;


    public static CoapServerBuilder builder() {
        return new CoapServerBuilder();
    }

    protected final void init() {
        init(DEFAULT_MAX_DUPLICATION_LIST_SIZE);
    }

    protected final void init(final int duplicationListSize) {
        if (transport == null) {
            throw new NullPointerException();
        }

        if (executor == null) {
            this.executor = Executors.newCachedThreadPool();
            isSelfCreatedExecutor = true;
        } else {
            isSelfCreatedExecutor = false;
        }

        if (scheduledExecutor == null) {
            if (executor instanceof ScheduledExecutorService) {
                scheduledExecutor = (ScheduledExecutorService) executor;
            } else {
                scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            }
        }

        if (idContext == null) {
            idContext = new MessageIdSupplierImpl();
        }
        if (transmissionTimeout == null) {
            this.transmissionTimeout = new CoapTimeout();
        }

        if (duplicationListSize > 0) {
            duplicationDetector = new DuplicationDetector(TimeUnit.MILLISECONDS, DEFAULT_DUPLICATION_TIMEOUT, duplicationListSize, scheduledExecutor);
        }
        if (errorCallback == null) {
            errorCallback = CoapErrorCallback.NULL;
        }
    }

    public void setEndpointQueueMaximumSize(int maximumEndpointQueueSize) {
        transMgr.setMaximumEndpointQueueSize(maximumEndpointQueueSize);
    }

    public void setDefaultCoapTransactionPriority(CoapTransaction.Priority defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    void setExecutor(Executor executor) {
        assertNotRunning();
        this.executor = executor;
    }

    protected void setMidSupplier(MessageIdSupplier idContext) {
        assertNotRunning();
        this.idContext = idContext;
    }

    @Override
    protected DuplicationDetector getDuplicationDetector() {
        return duplicationDetector;
    }

    /**
     * Sets scheduled executor to be used for periodically executed tasks.
     *
     * @param scheduledExecutor scheduled executor
     */
    public void setScheduledExecutor(ScheduledExecutorService scheduledExecutor) {
        assertNotRunning();
        if (scheduledExecutor == null) {
            throw new NullPointerException();
        }
        this.scheduledExecutor = scheduledExecutor;
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
        transport.start(this);
        if (duplicationDetector != null) {
            duplicationDetector.start();
        }
        startTransactionTimeoutWorker();
        isRunning = true;
        return this;
    }

    protected void assertNotRunning() throws IllegalStateException {
        if (isRunning) {
            throw new IllegalStateException("CoapServer is running");
        }
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

        LOGGER.finest("Stopping CoAP server..");
        stopTransactionTimeoutWorker();
        transport.stop();

        if (executor instanceof ExecutorService && isSelfCreatedExecutor) {
            ExecutorService srv = (ExecutorService) executor;
            srv.shutdown();
        }
        if (isSelfCreatedExecutor) {
            scheduledExecutor.shutdown();
        }
        LOGGER.fine("CoAP Server stopped");
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
     * Sets parameter for delayed transaction timeout
     *
     * @param delayedTransactionTimeout timeout in milliseconds
     */
    public void setDelayedTransactionTimeout(long delayedTransactionTimeout) {
        this.delayedTransactionTimeout = delayedTransactionTimeout;
    }

    public void setErrorCallback(CoapErrorCallback errorCallback) {
        if (errorCallback == null) {
            throw new NullPointerException();
        }
        this.errorCallback = errorCallback;
    }

    TransportConnector getTransport() {
        return transport;
    }

    @Deprecated
    public void setResponseTimeout(TransmissionTimeout responseTimeout) {
        this.transmissionTimeout = responseTimeout;
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
        LOGGER.fine("Handler added on " + uri);
    }

    /**
     * Removes request handler from server
     *
     * @param requestHandler request handler
     */
    public void removeRequestHandler(CoapHandler requestHandler) {
        UriMatcher url = findKey(requestHandler);
        if (url != null) {
            handlers.remove(url);
        }
    }

    /**
     * Sets block size that will be use for received messages
     *
     * @param blockSize block size
     */
    public final void setBlockSize(BlockSize blockSize) {
        this.blockOptionSize = blockSize;
    }

    /**
     * Returns defines block size
     *
     * @return block size
     */
    public final BlockSize getBlockSize() {
        return blockOptionSize;
    }

    /**
     * Sets maximum incoming entity size during block transfer
     * Zero means unlimited.
     *
     * @param maxBlockTransferSize - max transfer size
     */
    public final void setMaxIncomingBlockTransferSize(int maxBlockTransferSize) {
        this.maxIncomingBlockTransferSize = maxBlockTransferSize;
    }

    public final int getMaxIncomingBlockTransferSize() {
        return this.maxIncomingBlockTransferSize;
    }

    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return transport.getLocalSocketAddress();
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
        try {
            makeRequest(requestPacket, completableFuture, transContext);
        } catch (Exception e) {
            completableFuture.callException(e);
        }
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
     * @throws CoapException throws exception if request can not be send
     */
    public final void makeRequest(CoapPacket packet, Callback<CoapPacket> callback) throws CoapException {
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
     * @throws CoapException throws exception if request can not be send
     */
    public void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext) throws CoapException {
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
     * @throws CoapException throws exception if request can not be send
     */
    protected void makeRequestInternal(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext, CoapTransaction.Priority transactionPriority) throws CoapException {
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
     * @param callback handles response
     * @param transContext transport context that will be passed to transport connector
     * @param transactionPriority defines transaction priority (used by CoapServerBlocks mostyl)
     * @param forceAddToQueue forces add to queue even if there is queue limit overflow (block requests)
     * @throws CoapException throws exception if request can not be send
     */
    protected void makeRequestInternal(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext, CoapTransaction.Priority transactionPriority, boolean forceAddToQueue) throws CoapException {
        if (callback == null || packet == null || packet.getRemoteAddress() == null) {
            throw new NullPointerException();
        }

        //assign new MID
        packet.setMessageId(getNextMID());

        CoapTransaction trans = null;
        try {
            if (packet.getMustAcknowledge()) {
                trans = new CoapTransaction(callback, packet, this, transContext, transactionPriority);
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("makeRequestInternal: adding transaction: " + trans.toString() + ", forceAdd=" + forceAddToQueue);
                }
                if (transMgr.addTransactionAndGetReadyToSend(trans, forceAddToQueue)) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("makeRequestInternal: sending transaction: " + trans.toString() + ", forceAdd=" + forceAddToQueue);
                    }
                    trans.send(System.currentTimeMillis());
                }
            } else {
                //send NON message without waiting for piggy-backed response
                delayedTransMagr.add(new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress()), new CoapTransaction(callback, packet, this, transContext, transactionPriority));
                this.send(packet, packet.getRemoteAddress(), transContext);
                if (packet.getToken() == null || packet.getToken().length == 0) {
                    LOGGER.warning("Sent NON request without token: " + packet);
                }
            }

        } catch (TooManyRequestsForEndpointException ex) {
            throw ex;
        } catch (Throwable ex) {    //NOPMD it needs to catch anything, no control on customer resource implementation
            if (trans != null) {
                removeCoapTransId(trans.getTransactionId());
            }
            throw new CoapException(ex);
        }
    }

    /**
     * Sets handler for receiving notifications.
     *
     * @param observationHandler observation handler
     */
    public void setObservationHandler(ObservationHandler observationHandler) {
        this.observationHandler = observationHandler;
        LOGGER.finest("Observation handler set (" + observationHandler + ")");
    }

    @Override
    protected final void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        ByteArrayBackedOutputStream stream = new ByteArrayBackedOutputStream(coapPacket.getPayload() != null ? coapPacket.getPayload().length + 8 : 16);
        coapPacket.writeTo(stream);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("CoapServer.send(): MID:" + coapPacket.getMessageId() + "/" + adr);
        }

        transport.send(stream.getByteArray(), stream.size(), adr, tranContext);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("CoAP sent [" + coapPacket.toString(true) + "]");
        } else if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("CoAP sent [" + coapPacket.toString(false) + "]");
        }
        EVENT_LOGGER.info(EventLogger.COAP_SENT, adr, new EventLoggerCoapPacket(coapPacket));
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
    protected void handle(CoapPacket packet, TransportContext transportContext) {
        if (handlePing(packet)) {
            return;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("CoAP received [" + packet.toString(true) + "]");
        } else if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("CoAP received [" + packet.toString(false) + "]");
        }
        EVENT_LOGGER.info(EventLogger.COAP_RECEIVED, packet.getRemoteAddress(), new EventLoggerCoapPacket(packet));
        if (packet.getMethod() != null) {

            if (handleRequest(packet, transportContext)) {
                return;
            }
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

    /**
     * Handles parsing errors on incoming messages.
     */
    @Override
    protected void handleException(byte[] packet, CoapException exception, TransportContext transportContext) {
        errorCallback.parserError(packet, exception);
    }

    private boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null && packet.getMessageType() == MessageType.Confirmable) {
            LOGGER.fine("CoAP ping received.");
            CoapPacket resp = packet.createResponse(null);
            resp.setMessageType(MessageType.Reset);
            if (packet.getMessageType() == MessageType.NonConfirmable) {
                resp.setMessageId(getNextMID());
            }
            try {
                send(resp, packet.getRemoteAddress(), TransportContext.NULL);
                putToDuplicationDetector(packet, resp);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
            return true;
        }
        return false;
    }

    private boolean handleObservation(CoapPacket packet, TransportContext context) {
        if (packet.headers().getObserve() != null || (observationHandler != null && observationHandler.hasObservation(packet.getToken()))) {
            if (packet.getMessageType() == MessageType.Reset || packet.headers().getObserve() == null
                    || (packet.getCode() != Code.C205_CONTENT && packet.getCode() != Code.C203_VALID)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Notification termination [" + packet.toString() + "]");
                }
                if (observationHandler != null) {
                    //CoapExchange exchange = new CoapExchangeImpl(packet, this);
                    observationHandler.callException(new ObservationTerminatedException(packet, context));
                    return true;
                }
            }
            if (observationHandler != null) {
                if (!findDuplicate(packet, "CoAP notification repeated")) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("Notification [" + packet.getRemoteAddress() + "]");
                    }
                    CoapExchange exchange = new CoapExchangeImpl(packet, this, context);
                    observationHandler.call(exchange);
                }
                return true;
            }
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
            try {
                send(resp, packet.getRemoteAddress(), TransportContext.NULL);
                putToDuplicationDetector(packet, resp);
                LOGGER.warning("Can not process CoAP message [" + packet + "] sent RESET message");
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } else {
            handleNotProcessedMessageWeAreNotRespondingTo(packet);
        }
    }

    private static void handleNotProcessedMessageWeAreNotRespondingTo(CoapPacket packet) {
        if (MessageType.Acknowledgement.equals(packet.getMessageType())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Discarding extra ACK: " + packet);
            }
            return;
        }
        LOGGER.warning("Can not process CoAP message [" + packet + "]");
    }

    private boolean handleDelayedResponse(CoapPacket packet) {
        DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CoapTransaction trans = delayedTransMagr.find(delayedTransactionId);

        if (trans != null) {
            delayedTransMagr.remove(delayedTransactionId);
            try {
                if (packet.getMustAcknowledge()) {
                    CoapPacket resp = packet.createResponse();
                    send(resp, packet.getRemoteAddress(), TransportContext.NULL);
                    putToDuplicationDetector(packet, resp);
                }
                if (trans.getCallback() != null) {
                    trans.getCallback().call(packet);
                }
            } catch (Exception ex) {
                try {
                    LOGGER.log(Level.SEVERE, "Error while handling delayed response: " + ex.getMessage(), ex);
                    CoapPacket resp = packet.createResponse(Code.C500_INTERNAL_SERVER_ERROR);
                    send(resp, packet.getRemoteAddress(), TransportContext.NULL);
                    putToDuplicationDetector(packet, resp);
                } catch (CoapException | IOException ex1) {
                    LOGGER.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }

            return true;
        }
        return false;
    }

    protected void removeCoapTransId(CoapTransactionId coapTransId) {
        boolean failed;
        do {
            failed = false;
            Optional<CoapTransaction> maybeNextTransaction = transMgr.unlockOrRemoveAndGetNext(coapTransId);
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("next transaction: " + maybeNextTransaction);
            }
            if (!maybeNextTransaction.isPresent()) {
                break;
            }

            CoapTransaction nextTransactionForEp = maybeNextTransaction.get();
            try {
                if (!nextTransactionForEp.send(System.currentTimeMillis())) {
                    throw new CoapTimeoutException(nextTransactionForEp);
                }
            } catch (Exception ex) {
                failed = true;
                nextTransactionForEp.getCallback().callException(ex);
                coapTransId = nextTransactionForEp.getTransactionId();
                LOGGER.fine("Next transaction " + coapTransId + " sending on response, resulted in exception: " + ex.getMessage());
            }
        } while (failed);
    }

    private void invokeCallbackAndRemoveTransaction(CoapTransaction transaction, CoapPacket packet) {
        // first call callback and only then remove transaction - important for CoapServerBlocks
        // in other way block transfer will be interrupted by other messages in the queue
        // of TransactionManager, because removeCoapTransId() also sends next message form the queue

        try {
            transaction.getCallback().call(packet);
        } finally {
            removeCoapTransId(transaction.getTransactionId());
        }

    }

    protected boolean handleResponse(CoapPacket packet) {
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
        if (packet.getCode() != null) {
            invokeCallbackAndRemoveTransaction(trans, packet);
            return true;
        } else if (packet.getMessageType() == MessageType.Reset) {
            invokeCallbackAndRemoveTransaction(trans, packet);
            return true;
        }

        if (packet.getMessageType() == MessageType.Acknowledgement
                && packet.getCode() == null && (trans.getCoapRequest().getMethod() == null)) {
            invokeCallbackAndRemoveTransaction(trans, packet);
            return true;
        }

        if (packet.getMessageType() == MessageType.Acknowledgement
                && packet.getCode() == null && packet.getToken() != null) {
            //delayed response
            DelayedTransactionId delayedTransactionId = new DelayedTransactionId(trans.getCoapRequest().getToken(), packet.getRemoteAddress());
            removeCoapTransId(trans.getTransactionId());
            delayedTransMagr.add(delayedTransactionId, trans);
            return true;
        }
        throw new RuntimeException("not handled transaction");
        //return false;
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

    private boolean handleRequest(CoapPacket request, TransportContext transportContext) {
        if (findDuplicate(request, "CoAP request repeated")) {
            return true;
        }
        String uri = request.headers().getUriPath();
        if (uri == null) {
            uri = "/";
        }
        CoapPacket errorResponse = null;

        if (uri.length() > 0) {
            CoapHandler coapHandler = findHandler(uri);

            if (coapHandler != null) {
                try {
                    if (enabledCriticalOptTest) {
                        request.headers().criticalOptTest();
                    }
                    callRequestHandler(request, coapHandler, transportContext);
                } catch (CoapCodeException ex) {
                    errorResponse = request.createResponse(ex.getCode());
                    if (ex.getMessage() != null) {
                        errorResponse.setPayload(ex.getMessage());
                    }
                } catch (CoapException ex) {
                    errorResponse = request.createResponse(Code.C500_INTERNAL_SERVER_ERROR);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Unexpected exception: " + ex.getMessage(), ex);
                    errorResponse = request.createResponse(Code.C500_INTERNAL_SERVER_ERROR);
                }
            } else {
                errorResponse = request.createResponse(Code.C404_NOT_FOUND);
            }
            if (errorResponse != null) {
                try {
                    send(errorResponse, request.getRemoteAddress(), TransportContext.NULL);
                    putToDuplicationDetector(request, errorResponse);
                } catch (CoapException ex) {
                    //problems with parsing
                    LOGGER.warning(ex.getMessage());
                } catch (IOException ex) {
                    //network problems
                    LOGGER.severe(ex.getMessage());
                }
            }
            return true;
        }

        return false;
    }

    private boolean findDuplicate(CoapPacket request, String message) {
        //request
        if (duplicationDetector != null) {
            CoapPacket duplResp = duplicationDetector.isMessageRepeated(request);
            if (duplResp != null) {
                if (duplResp != DuplicationDetector.EMPTY_COAP_PACKET) {
                    try {
                        sendPacket(duplResp, request.getRemoteAddress(), TransportContext.NULL);
                    } catch (CoapException | IOException coapException) {
                        LOGGER.log(Level.SEVERE, coapException.getMessage(), coapException);
                    }
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(message + ", resending response [" + request.toString() + "]");
                    }
                } else {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(message + ", no response available [" + request.toString() + "]");
                    }
                }

                errorCallback.duplicated(request);

                return true;
            }
        }
        return false;
    }

    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext transportContext) throws CoapException {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transportContext);
        coapHandler.handle(exchange);
    }

    protected void setTransportConnector(TransportConnector transportConnector) {
        assertNotRunning();
        this.transport = transportConnector;
    }

    private void resendTimeouts() {
        try {
            //find timeouts
            //            if (LOGGER.isLoggable(Level.FINEST)) {
            //                LOGGER.finest("resendTimeouts: " + System.identityHashCode(this) + ", time=" + System.currentTimeMillis());
            //            }

            final long currentTime = System.currentTimeMillis();
            Collection<CoapTransaction> transTimeOut = transMgr.findTimeoutTransactions(currentTime);
            for (CoapTransaction trans : transTimeOut) {
                if (trans.isTimedOut(currentTime)) {
                    try {
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.finest("resendTimeouts: try to resend timed out transaction [" + trans.toString() + "]");  //[" + trans.coapRequest + "]");
                        }
                        if (!trans.send(currentTime)) {
                            //final timeout, cannot resend, remove transaction
                            removeCoapTransId(trans.getTransactionId());
                            if (LOGGER.isLoggable(Level.FINEST)) {
                                LOGGER.finest("resendTimeouts: CoAP transaction final timeout [" + trans.toString() + "]");  //[" + trans.coapRequest + "]");
                            }
                            trans.getCallback().callException(new CoapTimeoutException(trans));

                        } else {
                            if (trans.getCallback() instanceof CoapTransactionCallback) {
                                ((CoapTransactionCallback) trans.getCallback()).messageResent();
                            }
                        }
                    } catch (Exception ex) {
                        removeCoapTransId(trans.getTransactionId());
                        LOGGER.warning("CoAP transaction [" + trans.getTransactionId() + "] retransmission caused exception: " + ex.getMessage());
                        trans.getCallback().callException(ex);
                    }
                }
            }

            Collection<CoapTransaction> delayedTransTimeOut = delayedTransMagr.findTimeoutTransactions(currentTime);
            for (CoapTransaction trans : delayedTransTimeOut) {
                if (trans.isTimedOut(currentTime)) {
                    //delayed timeout, remove transaction
                    delayedTransMagr.remove(trans.getDelayedTransId());
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("CoAP delayed transaction timeout [" + trans.getDelayedTransId() + "]");
                    }
                    trans.getCallback().callException(new CoapTimeoutException(trans));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
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
     * @throws CoapException coap exception
     */
    public abstract byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) throws CoapException;

    public abstract byte[] observe(CoapPacket request, final Callback<CoapPacket> respCallback, TransportContext transportContext) throws CoapException;
}
