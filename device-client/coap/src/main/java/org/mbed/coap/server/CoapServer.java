/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.server.internal.CoapServerAbstract;
import org.mbed.coap.server.internal.CoapTransaction;
import org.mbed.coap.server.internal.CoapTransactionId;
import org.mbed.coap.server.internal.DelayedTransactionId;
import org.mbed.coap.server.internal.DelayedTransactionManager;
import org.mbed.coap.server.internal.HandlerURI;
import org.mbed.coap.server.internal.TransactionManager;
import org.mbed.coap.transmission.CoapTimeout;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportConnectorTask;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportWorkerWrapper;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.EventLogger;
import org.mbed.coap.utils.EventLoggerCoapPacket;

/**
 * Implements CoAP server ( RFC 7252)
 *
 * @author szymon
 * @see <a href="http://www.rfc-editor.org/rfc/rfc7252.txt"
 * >http://www.rfc-editor.org/rfc/rfc7252.txt</a>
 */
public class CoapServer extends CoapServerAbstract implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(CoapServer.class.getName());
    private static final EventLogger EVENT_LOGGER = EventLogger.getLogger("coap");
    private boolean isRunning;
    private final Map<HandlerURI, CoapHandler> handlers = new HashMap<>();
    private static final int DEFAULT_DUPLICATION_TIMEOUT = 30000;
    private ScheduledExecutorService scheduledExecutor;
    private TransportConnector transport;
    private BlockSize blockOptionSize; //null: no blocking
    private boolean isSelfCreatedExecutor;
    protected TransactionManager transMgr = new TransactionManager();
    private final DelayedTransactionManager delayedTransMagr = new DelayedTransactionManager();
    private ObservationHandler observationHandler;
    private DuplicationDetector duplicationDetector;
    private CoapIdContext idContext;
    private boolean enabledCriticalOptTest = true;
    private ScheduledFuture<?> transactionTimeoutWorkerFut;

    protected CoapServer() {
        // nothing to initialize
    }

    /**
     * Constructor for CoAP server.
     *
     * @param trans UDPConnector instance
     * @param executor executor instance
     * @param idContext CoapIdContext instance
     */
    protected CoapServer(TransportConnector trans, Executor executor, CoapIdContext idContext) {
        this.idContext = idContext;
        this.transport = trans;
        this.executor = executor;
        init();
    }

    final void init() {
        init(10000);
    }

    final void init(final int duplicationListSize) {
        if (transport == null) {
            throw new NullPointerException();
        }
        if (transport instanceof TransportConnectorTask) {
            transport = new TransportWorkerWrapper((TransportConnectorTask) transport);
            LOGGER.finest("Created worker for transport");
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
            idContext = new CoapIdContextImpl();
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

    void setExecutor(Executor executor) {
        assertNotRunning();
        this.executor = executor;
    }

    void setCoapIdContext(CoapIdContext idContext) {
        assertNotRunning();
        this.idContext = idContext;
    }

    /**
     * Disables duplicate detector. Use with caution.
     */
    public void disableDuplicationDetector() {
        assertNotRunning();
        duplicationDetector = null;
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

    protected ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    /**
     * Starts CoAP server
     *
     * @throws IOException
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

    protected void startTransactionTimeoutWorker() {
        transactionTimeoutWorkerFut = scheduledExecutor.scheduleWithFixedDelay(new TransactionTimeoutWorker(),
                0, TransactionTimeoutWorker.DELAY, TimeUnit.MILLISECONDS);
    }

    protected void stopTransactionTimeoutWorker() {
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
    public void setDelayedTransactionTimeout(int delayedTransactionTimeout) {
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
     * Sets CoAP transmission timeout settings, use this to change default CoAP
     * timeout
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
    public int getNextMID() {
        return idContext.getNextMID();
    }

    /**
     * Adds handler for incoming requests. URI context can be absolute or with
     * postfix. Postfix can be a star sign (*) for example: /s/temp*, it means
     * that all request under /s/temp/ will be directed to a given handler.
     *
     * @param uri URI of a resource
     * @param coapHandler Handler object
     */
    public void addRequestHandler(String uri, CoapHandler coapHandler) {
        handlers.put(new HandlerURI(uri), coapHandler);
        LOGGER.fine("Handler added on " + uri);
    }

    /**
     * Removes request handler from server
     *
     * @param requestHandler request handler
     */
    public void removeRequestHandler(CoapHandler requestHandler) {
        HandlerURI url = findKey(requestHandler);
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
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return transport.getLocalSocketAddress();
    }

    private HandlerURI findKey(CoapHandler requestHandler) {
        //TODO: return multiple keys
        for (HandlerURI url : handlers.keySet()) {
            if (handlers.get(url) == requestHandler) {
                return url;
            }
        }
        return null;
    }

    /**
     * Makes CoAP request. Sends given packet to specified address. Reply is
     * called through asynchronous Callback interface.
     * <p/>
     * <i>Asynchronous method</i>
     * <p/>
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
     * Makes CoAP request. Sends given packet to specified address. Reply is
     * called through asynchronous Callback interface.
     * <p/>
     * <i>Asynchronous method</i>
     * <p/>
     * NOTE: If exception is thrown then callback will never be invoked.
     *
     * @param packet request packet
     * @param callback handles response
     * @param transContext transport context that will be passed to transport
     * connector
     * @throws CoapException throws exception if request can not be send
     */
    public void makeRequest(final CoapPacket packet, final Callback<CoapPacket> callback, final TransportContext transContext) throws CoapException {
        if (callback == null || packet == null || packet.getRemoteAddress() == null) {
            throw new NullPointerException();
        }

        //assign new MID
        packet.setMessageId(getNextMID());

        CoapTransaction trans = null;
        try {
            if (packet.getMustAcknowladge()) {
                trans = new CoapTransaction(callback, packet, this, transContext);
                transMgr.add(trans);
                trans.send(System.currentTimeMillis());
            } else {
                //send NON message without waiting for piggy-backed response
                delayedTransMagr.add(new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress()), new CoapTransaction(callback, packet, this, transContext));
                this.send(packet, packet.getRemoteAddress(), transContext);
                LOGGER.warning("Sent NON request without token: " + packet);
            }

        } catch (Throwable ex) {    //NOPMD it needs to catch anything, no control on customer resource implementation
            if (trans != null) {
                removeCoapTransId(trans.getTransactionId());
            }
            throw new CoapException(ex);
        }
    }

    /**
     * Sets handler for receiving notifications.
     */
    public void setObservationHandler(ObservationHandler observationHandler) {
        this.observationHandler = observationHandler;
        LOGGER.finest("Observation handler set (" + observationHandler + ")");
    }

    @Override
    protected void send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        ByteArrayBackedOutputStream stream = new ByteArrayBackedOutputStream(coapPacket.getPayload() != null ? coapPacket.getPayload().length + 8 : 16);
        coapPacket.writeTo(stream);

        transport.send(stream.getByteArray(), stream.size(), adr, tranContext);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("CoAP sent [" + coapPacket + "]");
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
            } else if (handleObservation(packet)) {
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
            if (resp.getMessageType() == MessageType.NonConfirmable) {
                resp.setMessageId(getNextMID());
            }
            try {
                send(resp, packet.getRemoteAddress(), TransportContext.NULL);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
            return true;
        }
        return false;
    }

    private boolean handleObservation(CoapPacket packet) {
        if (packet.headers().getObserve() != null || (observationHandler != null && observationHandler.hasObservation(packet.getToken()))) {
            if (packet.getMessageType() == MessageType.Reset || packet.headers().getObserve() == null
                    || (packet.getCode() != Code.C205_CONTENT && packet.getCode() != Code.C203_VALID)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("Notification termination [" + packet.toString() + "]");
                }
                if (observationHandler != null) {
                    //CoapExchange exchange = new CoapExchangeImpl(packet, this);
                    observationHandler.callException(new ObservationTerminatedException(packet));
                    return true;
                }
            }
            if (observationHandler != null) {
                if (!findDuplicate(packet, "CoAP notification repeated")) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("Notification [" + packet.getRemoteAddress() + "]");
                    }
                    CoapExchange exchange = new CoapExchangeImpl(packet, this);
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
            if (resp.getMessageType() == MessageType.NonConfirmable) {
                resp.setMessageId(getNextMID());
            }
            try {
                send(resp, packet.getRemoteAddress(), TransportContext.NULL);
                LOGGER.warning("Can not process CoAP message [" + packet + "] sent RESET message");
                return;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } else {
            LOGGER.warning("Can not process CoAP message [" + packet + "]");
        }
    }

    private boolean handleDelayedResponse(CoapPacket packet) {
        DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CoapTransaction trans = delayedTransMagr.find(delayedTransactionId);

        if (trans != null) {
            delayedTransMagr.remove(delayedTransactionId);
            try {
                trans.getCallback().call(packet);
                if (packet.getMustAcknowladge()) {
                    send(packet.createResponse(), packet.getRemoteAddress(), TransportContext.NULL);
                }
            } catch (Exception ex) {
                try {
                    send(packet.createResponse(Code.C500_INTERNAL_SERVER_ERROR), packet.getRemoteAddress(), TransportContext.NULL);
                    LOGGER.log(Level.SEVERE, "Error while handling delayed response: " + ex.getMessage(), ex);
                } catch (CoapException | IOException ex1) {
                    LOGGER.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }

            return true;
        }
        return false;
    }

    protected void removeCoapTransId(CoapTransactionId coapTransId) {
        transMgr.remove(coapTransId);
    }

    protected boolean handleResponse(CoapPacket packet) {
        //find corresponding transaction
        CoapTransactionId coapTransId = new CoapTransactionId(packet);

        CoapTransaction trans = transMgr.find(coapTransId);
        if (trans == null) {
            return false;
        }

        if (packet.getCode() != null) {
            removeCoapTransId(coapTransId);
            trans.getCallback().call(packet);
            return true;
        } else if (packet.getMessageType() == MessageType.Reset) {
            removeCoapTransId(coapTransId);
            trans.getCallback().call(packet);
            return true;
        }

        if (packet.getMessageType() == MessageType.Acknowledgement
                && packet.getCode() == null && (trans.getCoapRequest().getMethod() == null)) {
            removeCoapTransId(coapTransId);
            //transMgr.remove(coapTransId);
            trans.getCallback().call(packet);
            return true;
        }

        if (packet.getMessageType() == MessageType.Acknowledgement
                && packet.getCode() == null && packet.getToken() != null) {
            //delayed response
            DelayedTransactionId delayedTransactionId = new DelayedTransactionId(trans.getCoapRequest().getToken(), packet.getRemoteAddress());
            removeCoapTransId(coapTransId);
            delayedTransMagr.add(delayedTransactionId, trans);
            return true;
        }
        return false;
    }

    private CoapHandler findHandler(String uri) {

        CoapHandler handler = handlers.get(new HandlerURI(uri));

        if (handler == null) {
            for (HandlerURI uriKey : handlers.keySet()) {
                if (uriKey.isMatching(uri)) {
                    return handlers.get(uriKey);
                }
            }
        }
        return handler;
    }

    protected void sendResponse(CoapExchange exchange) {
        try {
            CoapPacket resp = exchange.getResponse();
            if (resp == null) {
                //nothing to send
                return;
            }
            if (resp.getMessageType() == MessageType.NonConfirmable) {
                resp.setMessageId(getNextMID());
            }
            this.send(resp, exchange.getRemoteAddress(), exchange.getResponseTransportContext());
            if (duplicationDetector != null) {
                duplicationDetector.putResponse(exchange.getRequest(), resp);
            }

        } catch (CoapException ex) {
            LOGGER.warning(ex.getMessage());
            try {
                send(exchange.getRequest().createResponse(Code.C500_INTERNAL_SERVER_ERROR), exchange.getRemoteAddress(), exchange.getResponseTransportContext());

            } catch (CoapException | IOException ex1) {
                //impossible ;)
                LOGGER.log(Level.SEVERE, ex1.getMessage(), ex1);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * Enable or disable test for critical options. If enabled and incoming coap
     * packet contains non-recognized critical option, server will send error
     * message (4.02 bad option)
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
                    if (duplicationDetector != null) {
                        duplicationDetector.putResponse(request, errorResponse);
                    }
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
                        send(duplResp, request.getRemoteAddress(), TransportContext.NULL);
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

    void setTransportConnector(TransportConnector transportConnector) {
        assertNotRunning();
        this.transport = transportConnector;
    }

    protected void resendTimeouts() {
        //find timeouts

        final long currentTime = System.currentTimeMillis();
        Collection<CoapTransaction> transTimeOut = transMgr.findTimeoutTransactions(currentTime);
        for (CoapTransaction trans : transTimeOut) {
            if (trans.isTimedOut(currentTime)) {
                try {
                    if (!trans.send(currentTime)) {
                        //final timeout, cannot resend, remove transaction
                        removeCoapTransId(trans.getTransactionId());
                        //transactions.remove(trans.getTransactionId());
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.finest("CoAP transaction timeout [" + trans.getTransactionId() + "]");  //[" + trans.coapRequest + "]");
                        }
                        trans.getCallback().callException(new CoapTimeoutException());

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
                trans.getCallback().callException(new CoapTimeoutException());

            }
        }

    }

    private class TransactionTimeoutWorker implements Runnable {

        public final static long DELAY = 1000;

        @Override
        public void run() {
            try {
                resendTimeouts();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
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
        for (Entry<HandlerURI, CoapHandler> entry : handlers.entrySet()) {
            HandlerURI uri = entry.getKey();
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

}
