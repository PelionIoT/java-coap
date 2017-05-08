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

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java8.util.Optional;
import java8.util.function.Predicate;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

/**
 * Immutable object that holds transaction queue.
 *
 * Created by szymon
 */
public class TransactionQueue {
    private static final TransactionQueue EMPTY = new TransactionQueue(Collections.emptyList(), Optional.empty());

    public static TransactionQueue of() {
        return EMPTY;
    }

    public static TransactionQueue of(CoapTransaction trans) {
        return new TransactionQueue(Collections.singletonList(trans), Optional.empty());
    }

    //DO NOT EXPOSE THIS:
    private final List<CoapTransaction> transactions;
    private final Optional<CoapTransactionId> lockedTransaction;

    //KEEP PRIVATE
    private TransactionQueue(List<CoapTransaction> transactions, Optional<CoapTransactionId> lockedTransaction) {
        this.transactions = transactions;
        this.lockedTransaction = lockedTransaction;
    }

    public Optional<CoapTransaction> head() {
        if (transactions.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(transactions.get(0));
        }
    }


    //TODO: return option
    public TransactionQueue add(CoapTransaction transaction, boolean forceAdd, int maximumSize, AtomicBoolean queueOverflow) {
        int lockedNum = lockedTransaction.isPresent() ? 1 : 0;

        if (!forceAdd && (transactions.size() + lockedNum) >= maximumSize) {
            queueOverflow.set(true);
            return this;
        } else {
            queueOverflow.set(false);
            return add(transaction);
        }
    }

    private TransactionQueue add(CoapTransaction transaction) {
        List<CoapTransaction> ret = new ArrayList<>(transactions.size() + 1);

        CoapTransaction last = null;
        boolean added = false;

        for (CoapTransaction existing : transactions) {
            if (!added &&
                    (last == null || last.getTransactionPriority().ordinal() == transaction.getTransactionPriority().ordinal())
                    && existing.getTransactionPriority().ordinal() > transaction.getTransactionPriority().ordinal()) {
                ret.add(transaction);
                added = true;
            }
            ret.add(existing);
            last = existing;
        }

        if (!added) {
            ret.add(transaction);
        }

        return new TransactionQueue(ret, lockedTransaction);
    }

    private int findPosition(Predicate<CoapTransaction> predicate) {
        int i = 0;
        for (CoapTransaction trans : transactions) {
            if (predicate.test(trans)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public Optional<TransactionQueue> unlockOrRemove(CoapTransactionId transId) {
        if (lockedTransaction.filter(transId::equals).isPresent()) {
            if (transactions.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new TransactionQueue(transactions, Optional.empty()));
            }
        } else {
            int pos = findPosition(transId::matches);

            if (pos >= 0) {
                List<CoapTransaction> newTransactions = removeFromTransactionList(pos);
                if (newTransactions.isEmpty() && !lockedTransaction.isPresent()) {
                    return Optional.empty();
                } else {
                    return Optional.of(new TransactionQueue(newTransactions, lockedTransaction));
                }
            }
            return Optional.of(this);
        }
    }

    private List<CoapTransaction> removeFromTransactionList(int removePos) {
        List<CoapTransaction> newTransList = new ArrayList<>(transactions.size() - 1);

        for (int i = 0; i < transactions.size(); i++) {
            if (i != removePos) {
                newTransList.add(transactions.get(i));
            }
        }
        return newTransList;
    }

    public QueueUpdateResult removeAndLock(CoapTransactionId transId) {
        int pos = findPosition(transId::matches);
        if (pos < 0) {
            return new QueueUpdateResult(this, Optional.empty());
        }

        CoapTransaction removedTransaction = transactions.get(pos);
        return new QueueUpdateResult(new TransactionQueue(removeFromTransactionList(pos), Optional.of(transId)), Optional.of(removedTransaction));
    }

    TransactionQueue lock(CoapTransactionId transId) {
        if (lockedTransaction.isPresent()) {
            throw new IllegalStateException();
        }

        return new TransactionQueue(this.transactions, Optional.of(transId));
    }

    public QueueUpdateResult findAndRemoveSeparateResponse(CoapPacket req) {
        int pos = findPosition(trans -> isMatchForSeparateResponse(trans, req));

        if (pos < 0) {
            return new QueueUpdateResult(this, Optional.empty());
        } else {
            CoapTransaction removedTransaction = transactions.get(pos);
            //note, not locking!
            return new QueueUpdateResult(new TransactionQueue(removeFromTransactionList(pos), Optional.empty()), Optional.of(removedTransaction));
        }
    }

    private static boolean isMatchForSeparateResponse(CoapTransaction trans, CoapPacket packet) {
        return trans.isActive() &&
                (packet.getMessageType() == MessageType.Confirmable || packet.getMessageType() == MessageType.NonConfirmable)
                && packet.getCode() != null
                && trans.coapRequest.getRemoteAddress().equals(packet.getRemoteAddress())
                && Arrays.equals(trans.coapRequest.getToken(), packet.getToken());
    }


    public int size() {
        return transactions.size();
    }

    public Stream<CoapTransaction> stream() {
        return StreamSupport.stream(transactions);
    }

    public boolean notLocked() {
        return !lockedTransaction.isPresent();
    }

    public static class QueueUpdateResult {
        final TransactionQueue transactionQueue;
        final Optional<CoapTransaction> coapTransaction;

        public QueueUpdateResult(TransactionQueue transactionQueue, Optional<CoapTransaction> coapTransaction) {
            this.transactionQueue = transactionQueue;
            this.coapTransaction = coapTransaction;
        }
    }
}

