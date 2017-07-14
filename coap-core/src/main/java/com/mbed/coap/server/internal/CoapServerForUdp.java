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
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.CoapTransactionCallback;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
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
 *
 * @author szymon
 * @see <a href="http://www.rfc-editor.org/rfc/rfc7252.txt" >http://www.rfc-editor.org/rfc/rfc7252.txt</a>
 */
public abstract class CoapServerForUdp extends CoapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerForUdp.class);
    private final static long TRANSACTION_TIMEOUT_DELAY = 1000;
    private static final int DEFAULT_DUPLICATION_TIMEOUT = 30000;
    private ScheduledExecutorService scheduledExecutor;

    private BlockSize blockOptionSize; //null: no blocking
    private boolean isSelfCreatedExecutor;
    private final TransactionManager transMgr = new TransactionManager();
    private final DelayedTransactionManager delayedTransMagr = new DelayedTransactionManager();
    private DuplicationDetector duplicationDetector;
    private MessageIdSupplier idContext;
    private ScheduledFuture<?> transactionTimeoutWorkerFut;
    private int maxIncomingBlockTransferSize;
    private CoapTransaction.Priority defaultPriority;
    protected long delayedTransactionTimeout;
    protected TransmissionTimeout transmissionTimeout;
    protected DuplicatedCoapMessageCallback duplicatedCoapMessageCallback;


    public static CoapServerBuilder builder() {
        return CoapServerBuilder.newBuilder();
    }

    public final void init(int duplicationListSize, CoapTransport coapTransporter,
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

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    @Override
    public synchronized CoapServer start() throws IOException, IllegalStateException {
        super.start();
        if (duplicationDetector != null) {
            duplicationDetector.start();
        }
        startTransactionTimeoutWorker();
        return this;
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

    @Override
    public void stop0() {
        if (duplicationDetector != null) {
            duplicationDetector.stop();
        }

        stopTransactionTimeoutWorker();

        if (isSelfCreatedExecutor) {
            scheduledExecutor.shutdown();
        }
    }


    /**
     * Sets CoAP transmission timeout settings, use this to change default CoAP timeout
     *
     * @param transmissionTimeout transmission timeout
     */
    public void setTransmissionTimeout(TransmissionTimeout transmissionTimeout) {
        this.transmissionTimeout = transmissionTimeout;
    }

    @Override
    public int getNextMID() {
        return idContext.getNextMID();
    }


    @Override
    public final BlockSize getBlockSize() {
        return blockOptionSize;
    }

    final int getMaxIncomingBlockTransferSize() {
        return this.maxIncomingBlockTransferSize;
    }

    long getDelayedTransactionTimeout() {
        return delayedTransactionTimeout;
    }

    TransmissionTimeout getTransmissionTimeout() {
        return transmissionTimeout;
    }

    @Override
    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp, TransportContext ctx) {
        putToDuplicationDetector(request, resp);
        send(resp, request.getRemoteAddress(), ctx);
    }

    protected final void putToDuplicationDetector(CoapPacket request, CoapPacket response) {
        if (duplicationDetector != null) {
            duplicationDetector.putResponse(request, response);
        }
    }

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

    CompletableFuture<Boolean> send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        if (coapPacket.getMessageType() == MessageType.NonConfirmable) {
            coapPacket.setMessageId(getNextMID());
        }
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

    @Override
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

    @Override
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


}
