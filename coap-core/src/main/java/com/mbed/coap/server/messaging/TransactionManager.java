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

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class TransactionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionManager.class.getName());
    private final ConcurrentHashMap<CoapTransactionId, CoapTransaction> transactions = new ConcurrentHashMap<>();

    public void put(CoapTransaction transaction) {
        if (transactions.putIfAbsent(transaction.getTransactionId(), transaction) != null) {
            throw new RuntimeException("Transaction already exists");
        }
    }

    public Optional<CoapTransaction> getAndRemove(CoapTransactionId transId) {
        return Optional.ofNullable(transactions.remove(transId));
    }

    public void remove(CoapTransactionId transId) {
        transactions.remove(transId);
    }

    public int getNumberOfTransactions() {
        return transactions.size();
    }


    public Optional<CoapTransaction> findMatchAndRemoveForSeparateResponse(CoapPacket req) {
        if ((req.getMessageType() == MessageType.Confirmable || req.getMessageType() == MessageType.NonConfirmable)
                && req.getCode() != null && req.getToken().nonEmpty()) {

            return transactions.values().stream()
                    .filter(trans -> isMatchForSeparateResponse(trans, req))
                    .findFirst()
                    .map(trans -> transactions.remove(trans.getTransactionId()));

        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("findMatchAndRemoveForSeparateResponse(" + req.toString(false) + "): not found");
            }
            return Optional.empty();
        }
    }

    public Stream<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        return transactions.values().stream()
                .filter(trans -> trans.isTimedOut(currentTime));
    }

    public void close() {
        Enumeration<CoapTransactionId> keys = transactions.keys();

        while (keys.hasMoreElements()) {
            CoapTransactionId key = keys.nextElement();
            CoapTransaction trans = transactions.remove(key);
            if (trans != null) {
                trans.promise.completeExceptionally(new IOException("Server stopped"));
            }
        }

    }

    private boolean isMatchForSeparateResponse(CoapTransaction trans, CoapPacket packet) {
        return (packet.getMessageType() == MessageType.Confirmable || packet.getMessageType() == MessageType.NonConfirmable)
                && packet.getCode() != null
                && trans.coapRequest.getRemoteAddress().equals(packet.getRemoteAddress())
                && Objects.equals(trans.coapRequest.getToken(), packet.getToken());
    }
}
