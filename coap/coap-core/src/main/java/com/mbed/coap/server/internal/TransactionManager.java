/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.internal.TransactionQueue.QueueUpdateResult;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class TransactionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionManager.class.getName());
    private final ConcurrentHashMap<InetSocketAddress, TransactionQueue> transactionQueues = new ConcurrentHashMap<>();
    private int maximumEndpointQueueSize = 100;


    public void setMaximumEndpointQueueSize(int maximumEndpointQueueSize) {
        if (maximumEndpointQueueSize < 1 || maximumEndpointQueueSize > 65536) {
            throw new IllegalArgumentException("Endpoint queue size should be in range 1..65536");
        }
        this.maximumEndpointQueueSize = maximumEndpointQueueSize;
    }

    boolean addTransactionAndGetReadyToSend(CoapTransaction transaction) throws TooManyRequestsForEndpointException {
        return addTransactionAndGetReadyToSend(transaction, false);
    }

    @SuppressWarnings("PMD.PrematureDeclaration") //false positive
    public boolean addTransactionAndGetReadyToSend(CoapTransaction transaction, boolean forceAdd) throws TooManyRequestsForEndpointException {
        AtomicBoolean queueOverflow = new AtomicBoolean(false);

        TransactionQueue inserted = transactionQueues.compute(transaction.getTransactionId().getAddress(), (address, coapTransactions) -> {
            if (coapTransactions == null) {
                return TransactionQueue.of(transaction);
            } else {
                return coapTransactions.add(transaction, forceAdd, maximumEndpointQueueSize, queueOverflow);
            }
        });

        if (queueOverflow.get()) {
            throw new TooManyRequestsForEndpointException("TOO_MANY_REQUESTS maximum allowed per endpoint " + maximumEndpointQueueSize);
        }

        return inserted.size() == 1 && inserted.notLocked();
    }

    public Optional<CoapTransaction> removeAndLock(CoapTransactionId transId) {
        AtomicReference<Optional<CoapTransaction>> transactionFound = new AtomicReference<>(Optional.empty());

        transactionQueues.computeIfPresent(transId.getAddress(), (address, coapTransactions) -> {
            QueueUpdateResult result = coapTransactions.removeAndLock(transId);

            transactionFound.set(result.coapTransaction);
            return result.transactionQueue;
        });

        return transactionFound.get();
    }

    public Optional<CoapTransaction> unlockOrRemoveAndGetNext(CoapTransactionId transId) {

        return Optional.ofNullable(
                transactionQueues.computeIfPresent(transId.getAddress(),
                        (address, coapTransactions) -> coapTransactions.unlockOrRemove(transId).orElse(null)
                )
        ).flatMap(TransactionQueue::head);
    }

    public int getNumberOfTransactions() {
        return transactionQueues.values().stream().mapToInt(TransactionQueue::size).sum();
    }


    public Optional<CoapTransaction> findMatchAndRemoveForSeparateResponse(CoapPacket req) {
        if ((req.getMessageType() == MessageType.Confirmable || req.getMessageType() == MessageType.NonConfirmable)
                && req.getCode() != null && req.getToken().length > 0) {

            AtomicReference<Optional<CoapTransaction>> transactionFound = new AtomicReference<>(Optional.empty());

            transactionQueues.computeIfPresent(req.getRemoteAddress(), (address, coapTransactions) -> {
                QueueUpdateResult result = coapTransactions.findAndRemoveSeparateResponse(req);

                transactionFound.set(result.coapTransaction);
                return result.transactionQueue;
            });

            return transactionFound.get();
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("findMatchAndRemoveForSeparateResponse(" + req.toString(false) + "): not found");
            }
            return Optional.empty();
        }
    }

    public Collection<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        Collection<CoapTransaction> ret = transactionQueues.values().stream()
                .flatMap(TransactionQueue::stream)
                .filter(trans -> trans.isTimedOut(currentTime))
                .collect(Collectors.toList());
        return ret;
    }

}
