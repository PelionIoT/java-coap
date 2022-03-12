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
package com.mbed.coap.server.messaging;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class DelayedTransactionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelayedTransactionManager.class.getName());
    private final ConcurrentMap<DelayedTransactionId, CoapTransaction> transactions = new ConcurrentHashMap<>();

    public void add(DelayedTransactionId delayedTransactionId, CoapTransaction trans) {
        trans.setDelayedTransId(delayedTransactionId);
        transactions.put(delayedTransactionId, trans);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Added delayed transaction: " + delayedTransactionId);
        }
    }

    public CoapTransaction find(DelayedTransactionId delayedTransactionId) {
        return transactions.get(delayedTransactionId);
    }

    public void remove(DelayedTransactionId delayedTransactionId) {
        transactions.remove(delayedTransactionId);
    }

    public Collection<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        Collection<CoapTransaction> transTimeOut = new LinkedList<>();
        for (Map.Entry<DelayedTransactionId, CoapTransaction> trans : transactions.entrySet()) {
            if (trans.getValue().isTimedOut(currentTime)) {
                transTimeOut.add(trans.getValue());
            }
        }
        return transTimeOut;
    }

    public void close() {
        for (CoapTransaction t : transactions.values()) {
            t.promise.completeExceptionally(new IOException("Server stopped"));
        }
        transactions.clear();
    }
}
