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
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.internal.TransactionQueue.QueueUpdateResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java8.util.Optional;
import java8.util.function.BiFunction;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
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

        TransactionQueue inserted = compute(transaction.getTransactionId().getAddress(), (address, coapTransactions) -> {
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

        computeIfPresent(transId.getAddress(), (address, coapTransactions) -> {
            QueueUpdateResult result = coapTransactions.removeAndLock(transId);

            transactionFound.set(result.coapTransaction);
            return result.transactionQueue;
        });

        return transactionFound.get();
    }

    public Optional<CoapTransaction> unlockOrRemoveAndGetNext(CoapTransactionId transId) {

        return Optional.ofNullable(
                computeIfPresent(transId.getAddress(),
                        (address, coapTransactions) -> coapTransactions.unlockOrRemove(transId).orElse(null)
                )
        ).flatMap(TransactionQueue::head);
    }

    public int getNumberOfTransactions() {
        int sum = 0;
        for (TransactionQueue transactionQueue : transactionQueues.values()) {
            int size = transactionQueue.size();
            sum += size;
        }
        return sum;
    }


    public Optional<CoapTransaction> findMatchAndRemoveForSeparateResponse(CoapPacket req) {
        if ((req.getMessageType() == MessageType.Confirmable || req.getMessageType() == MessageType.NonConfirmable)
                && req.getCode() != null && req.getToken().length > 0) {

            AtomicReference<Optional<CoapTransaction>> transactionFound = new AtomicReference<>(Optional.empty());

            computeIfPresent(req.getRemoteAddress(), (address, coapTransactions) -> {
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
        Collection<CoapTransaction> ret = StreamSupport.stream(transactionQueues.values())
                .flatMap(TransactionQueue::stream)
                .filter(trans -> trans.isTimedOut(currentTime))
                .collect(Collectors.toList());
        return ret;
    }

    public void close() {
        Enumeration<InetSocketAddress> keys = transactionQueues.keys();

        while (keys.hasMoreElements()) {
            InetSocketAddress key = keys.nextElement();
            TransactionQueue transQueue = transactionQueues.remove(key);
            if (transQueue != null) {
                transQueue.stream().forEach(t -> t.getCallback().callException(new IOException("Server stopped")));
            }
        }
    }

    //---- java7 backport -----
    private TransactionQueue compute(InetSocketAddress key, BiFunction<InetSocketAddress, TransactionQueue, TransactionQueue> remapping) {

        TransactionQueue oldVal = transactionQueues.get(key);
        TransactionQueue newVal = remapping.apply(key, oldVal);
        while (!replaceOrRemove(key, oldVal, newVal)) {
            //retry if concurrent modification appears
            oldVal = transactionQueues.get(key);
            newVal = remapping.apply(key, oldVal);
        }

        return newVal;
    }

    private TransactionQueue computeIfPresent(InetSocketAddress key, BiFunction<InetSocketAddress, TransactionQueue, TransactionQueue> remapping) {

        TransactionQueue oldVal = transactionQueues.get(key);
        TransactionQueue newVal = oldVal != null ? remapping.apply(key, oldVal) : null;
        while (oldVal != null && !replaceOrRemove(key, oldVal, newVal)) {
            //retry if concurrent modification appears
            oldVal = transactionQueues.get(key);
            newVal = remapping.apply(key, oldVal);
        }

        return newVal;
    }

    private boolean replaceOrRemove(InetSocketAddress key, TransactionQueue oldVal, TransactionQueue newVal) {
        if (oldVal == null) {
            if (newVal != null) {
                return transactionQueues.putIfAbsent(key, newVal) == null;
            } else {
                return !transactionQueues.containsKey(key);
            }
        }
        if (newVal != null) {
            return transactionQueues.replace(key, oldVal, newVal);
        } else {
            return transactionQueues.remove(key, oldVal);
        }
    }
}
