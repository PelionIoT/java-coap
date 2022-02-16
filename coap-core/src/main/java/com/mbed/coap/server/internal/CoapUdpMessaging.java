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
package com.mbed.coap.server.internal;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapRequestId;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.server.PutOnlyMap;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements CoAP server ( RFC 7252)
 * @see <a href="http://www.rfc-editor.org/rfc/rfc7252.txt" >http://www.rfc-editor.org/rfc/rfc7252.txt</a>
 */
public class CoapUdpMessaging extends CoapMessaging {
    public static final long DEFAULT_DUPLICATION_TIMEOUT_MILLIS = 30000; // Duplicate detection will end for a message after 30 seconds by default.
    public static final long DEFAULT_WARN_INTERVAL_MILLIS = 10000; //show warning message maximum every 10 seconds by default
    public static final long DEFAULT_CLEAN_INTERVAL_MILLIS = 10000; //clean expired messages every 10 seconds by default

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapUdpMessaging.class);
    private final static long TRANSACTION_TIMEOUT_DELAY_MILLIS = 1000;
    private ScheduledExecutorService scheduledExecutor;
    private DefaultDuplicateDetectorCache cache;

    private boolean isSelfCreatedExecutor;
    private final TransactionManager transMgr = new TransactionManager();
    private final DelayedTransactionManager delayedTransMagr = new DelayedTransactionManager();
    private DuplicationDetector duplicationDetector;
    private MessageIdSupplier idContext;
    private ScheduledFuture<?> transactionTimeoutWorkerFut;
    protected long delayedTransactionTimeout;
    protected TransmissionTimeout transmissionTimeout;
    protected DuplicatedCoapMessageCallback duplicatedCoapMessageCallback;

    public CoapUdpMessaging(CoapTransport coapTransport) {
        super(coapTransport);
    }

    public final void init(
            PutOnlyMap<CoapRequestId, CoapPacket> cache,
            boolean isSelfCreatedExecutor,
            MessageIdSupplier idContext,
            int maxQueueSize,
            long delayedTransactionTimeout,
            DuplicatedCoapMessageCallback duplicatedCoapMessageCallback,
            ScheduledExecutorService scheduledExecutor
    ) {
        if (coapTransporter == null || scheduledExecutor == null || idContext == null || duplicatedCoapMessageCallback == null || cache == null) {
            throw new NullPointerException();
        }
        if (cache instanceof DefaultDuplicateDetectorCache) {
            this.cache = (DefaultDuplicateDetectorCache) cache;
        }
        this.duplicationDetector = new DuplicationDetector(cache);
        this.scheduledExecutor = scheduledExecutor;
        this.isSelfCreatedExecutor = isSelfCreatedExecutor;
        this.idContext = idContext;
        this.delayedTransactionTimeout = delayedTransactionTimeout;
        this.duplicatedCoapMessageCallback = duplicatedCoapMessageCallback;

        transMgr.setMaximumEndpointQueueSize(maxQueueSize);

        if (transmissionTimeout == null) {
            this.transmissionTimeout = new CoapTimeout();
        }
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    @Override
    public synchronized void start(CoapRequestHandler coapRequestHandler) throws IOException, IllegalStateException {
        if (cache != null) {
            cache.start();
        }
        startTransactionTimeoutWorker();
        super.start(coapRequestHandler);
    }

    private void startTransactionTimeoutWorker() {
        transactionTimeoutWorkerFut = scheduledExecutor.scheduleWithFixedDelay(this::resendTimeouts,
                0, TRANSACTION_TIMEOUT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void stopTransactionTimeoutWorker() {
        if (transactionTimeoutWorkerFut != null) {
            transactionTimeoutWorkerFut.cancel(false);
            transactionTimeoutWorkerFut = null;
        }
    }

    @Override
    public void stop0() {
        if (cache != null) {
            cache.stop();
        }

        stopTransactionTimeoutWorker();

        if (isSelfCreatedExecutor) {
            scheduledExecutor.shutdown();
        }

        transMgr.close();
        delayedTransMagr.close();
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
    private int getNextMID() {
        return idContext.getNextMID();
    }


    long getDelayedTransactionTimeout() {
        return delayedTransactionTimeout;
    }

    TransmissionTimeout getTransmissionTimeout() {
        return transmissionTimeout;
    }

    @Override
    public void sendResponse(CoapPacket request, CoapPacket resp, TransportContext ctx) {
        putToDuplicationDetector(request, resp);

        if (resp.getMessageType() == MessageType.NonConfirmable || request.getMessageType() == MessageType.NonConfirmable) {
            resp.setMessageId(getNextMID());
        }

        send(resp, request.getRemoteAddress(), ctx);
    }

    private final void putToDuplicationDetector(CoapPacket request, CoapPacket response) {
        if (duplicationDetector != null) {
            duplicationDetector.putResponse(request, response);
        }
    }

    @Override
    public CompletableFuture<CoapPacket> makeRequest(final CoapPacket packet, final TransportContext transContext) {
        boolean forceAddToQueue = false;
        if ((packet.headers().getBlock1Req() != null && packet.headers().getBlock1Req().getNr() > 0) || (packet.headers().getBlock2Res() != null && packet.headers().getBlock2Res().getNr() > 0)) {
            forceAddToQueue = true;
        }
        return makeRequestInternal(packet, transContext, forceAddToQueue);
    }

    /**
     * Makes CoAP request. Sends given packet to specified address.
     * <p>
     * <i>Asynchronous method</i>
     * </p>
     *
     * @param packet request packet
     * @param transContext transport context that will be passed to transport connector
     * @param forceAddToQueue forces add to queue even if there is queue limit overflow (block requests)
     */
    private CompletableFuture<CoapPacket> makeRequestInternal(final CoapPacket packet, final TransportContext transContext, boolean forceAddToQueue) {
        if (packet == null || packet.getRemoteAddress() == null) {
            throw new NullPointerException();
        }

        //assign new MID
        packet.setMessageId(getNextMID());

        if (packet.getMustAcknowledge()) {
            CoapTransaction trans = new CoapTransaction(packet, this, transContext, this::removeCoapTransId);
            try {
                if (transMgr.addTransactionAndGetReadyToSend(trans, forceAddToQueue)) {
                    LOGGER.trace("Sending transaction: {}", trans);
                    trans.send();
                } else {
                    LOGGER.trace("Enqueued transaction: {}", trans);
                }
            } catch (TooManyRequestsForEndpointException e) {
                trans.promise.completeExceptionally(e);
            }
            return trans.promise;
        } else {
            //send NON message without waiting for piggy-backed response
            DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
            CoapTransaction trans = new CoapTransaction(packet, this, transContext, this::removeCoapTransId);
            delayedTransMagr.add(delayedTransactionId, trans);
            this.send(packet, packet.getRemoteAddress(), transContext)
                    .whenComplete((wasSent, maybeError) -> {
                        if (maybeError != null) {
                            delayedTransMagr.remove(delayedTransactionId);
                            trans.promise.completeExceptionally(maybeError);
                        }
                    });
            if (packet.getToken().isEmpty()) {
                LOGGER.warn("Sent NON request without token: {}", packet);
            }
            return trans.promise;
        }
    }

    CompletableFuture<Boolean> send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        return sendPacket(coapPacket, adr, tranContext);
    }

    /**
     * Returns number of waiting transaction.
     *
     * @return number of transactions
     */
    public int getNumberOfTransactions() {
        return transMgr.getNumberOfTransactions();
    }

    @Override
    protected boolean handleDelayedResponse(CoapPacket packet) {
        DelayedTransactionId delayedTransactionId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CoapTransaction trans = delayedTransMagr.find(delayedTransactionId);

        if (trans != null) {
            delayedTransMagr.remove(delayedTransactionId);
            if (packet.getMustAcknowledge()) {
                CoapPacket resp = packet.createResponse();
                sendResponse(packet, resp);
            }

            trans.complete(packet);
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

        transaction.complete(packet);
        removeCoapTransId(transaction.getTransactionId());
    }

    @Override
    protected void handleRequest(CoapPacket packet, TransportContext transContext) {
        if (findDuplicate(packet, "CoAP request repeated")) {
            return;
        }
        super.handleRequest(packet, transContext);
    }

    @Override
    protected boolean handleObservation(CoapPacket packet, TransportContext transportContext) {
        Integer observe = packet.headers().getObserve();
        if (observe == null) {
            return false;
        }
        if (findDuplicate(packet, "CoAP notification repeated")) {
            return true;
        }
        return super.handleObservation(packet, transportContext);
    }

    @Override
    protected boolean handleResponse(CoapPacket packet) {
        //find corresponding transaction
        CoapTransactionId coapTransId = new CoapTransactionId(packet);

        Optional<CoapTransaction> maybeTrans = transMgr.removeAndLock(coapTransId);
        if (!maybeTrans.isPresent() && packet.getMessageType() == MessageType.Confirmable || packet.getMessageType() == MessageType.NonConfirmable) {
            //find if it is separate response
            maybeTrans = transMgr.findMatchAndRemoveForSeparateResponse(packet);
        }

        boolean packetHandled = maybeTrans
                .map(trans -> handleResponse(trans, packet))
                .orElse(false);

        if (packetHandled && packet.headers().getObserve() != null && packet.getMessageType() == MessageType.Acknowledgement) {
            // put the response to duplicate detector to avoid duplicate observation for retransmitted request.
            findDuplicate(packet, "CoAP notification repeated (for first response)");
        }

        return packetHandled;
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

    //    @Override
    protected boolean findDuplicate(CoapPacket request, String message) {
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
                        trans.promise.completeExceptionally(new CoapTimeoutException(trans));
                    }
                }
            }

            Collection<CoapTransaction> delayedTransTimeOut = delayedTransMagr.findTimeoutTransactions(currentTime);
            for (CoapTransaction trans : delayedTransTimeOut) {
                if (trans.isTimedOut(currentTime)) {
                    //delayed timeout, remove transaction
                    delayedTransMagr.remove(trans.getDelayedTransId());
                    LOGGER.trace("CoAP delayed transaction timeout [{}]", trans.getDelayedTransId());
                    trans.promise.completeExceptionally(new CoapTimeoutException(trans));
                }
            }
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }
}
