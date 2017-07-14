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

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.server.internal.CoapServerBlocks;
import com.mbed.coap.server.internal.CoapServerForUdp;
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author szymon
 */
public class CoapServerBuilder {
    private static final int DEFAULT_MAX_DUPLICATION_LIST_SIZE = 10000;
    private static final long DELAYED_TRANSACTION_TIMEOUT_MS = 120000; //2 minutes

    private final CoapServerBlocks server;
    private int duplicationMaxSize = DEFAULT_MAX_DUPLICATION_LIST_SIZE;
    private CoapTransport coapTransport;
    private ScheduledExecutorService scheduledExecutorService;
    private MessageIdSupplier midSupplier = new MessageIdSupplierImpl();
    private int maxIncomingBlockTransferSize;
    private CoapTransaction.Priority priority = CoapTransaction.Priority.NORMAL;
    private int maxQueueSize = 100;
    private BlockSize blockSize;
    private long delayedTransactionTimeout = DELAYED_TRANSACTION_TIMEOUT_MS;
    private DuplicatedCoapMessageCallback duplicatedCoapMessageCallback = DuplicatedCoapMessageCallback.NULL;

    CoapServerBuilder() {
        server = new CoapServerBlocks();
    }

    public static CoapServerBuilder newBuilder() {
        return new CoapServerBuilder();
    }

    public static CoapServer newCoapServer(CoapTransport transport) {
        return new CoapServerBuilder().transport(transport).build();
    }

    public CoapServerBuilder transport(int port) {
        this.coapTransport = new DatagramSocketTransport(new InetSocketAddress(port), Runnable::run);
        return this;
    }

    public CoapServerBuilder transport(int port, Executor receivedMessageWorker) {
        this.coapTransport = new DatagramSocketTransport(new InetSocketAddress(port), receivedMessageWorker);
        return this;
    }

    public CoapServerBuilder transport(CoapTransport coapTransport) {
        this.coapTransport = coapTransport;
        return this;
    }

    public CoapServerBuilder scheduledExecutor(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        return this;
    }

    public CoapServerBuilder midSupplier(MessageIdSupplier midSupplier) {
        this.midSupplier = midSupplier;
        return this;
    }

    public CoapServerBuilder timeout(TransmissionTimeout timeout) {
        server.setTransmissionTimeout(timeout);
        return this;
    }

    public CoapServerBuilder delayedTimeout(long delayedTransactionTimeout) {
        if (delayedTransactionTimeout <= 0) {
            throw new IllegalArgumentException();
        }
        this.delayedTransactionTimeout = delayedTransactionTimeout;
        return this;
    }

    public CoapServerBuilder blockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
        return this;
    }

    public CoapServerBuilder maxIncomingBlockTransferSize(int size) {
        this.maxIncomingBlockTransferSize = size;
        return this;
    }

    public CoapServerBuilder duplicatedCoapMessageCallback(DuplicatedCoapMessageCallback duplicatedCallback) {
        if (duplicatedCallback == null) {
            throw new NullPointerException();
        }
        this.duplicatedCoapMessageCallback = duplicatedCallback;
        return this;
    }

    public CoapServerBuilder defaultQueuePriority(CoapTransaction.Priority priority) {
        this.priority = priority;
        return this;
    }

    public CoapServerBuilder queueMaxSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    public CoapServerBuilder blockMessageTransactionQueuePriority(CoapTransaction.Priority priority) {
        server.setBlockCoapTransactionPriority(priority);
        return this;
    }

    /**
     * Sets maximum number or requests to be kept for duplication detection.
     *
     * @param duplicationMaxSize maximum size
     * @return this instance
     */
    public CoapServerBuilder duplicateMsgCacheSize(int duplicationMaxSize) {
        if (duplicationMaxSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.duplicationMaxSize = duplicationMaxSize;
        return this;
    }

    public CoapServerBuilder disableDuplicateCheck() {
        this.duplicationMaxSize = -1;
        return this;
    }

    public CoapServer start() throws IOException {
        return build().start();
    }

    public CoapServerForUdp build() {
        if (coapTransport == null) {
            throw new IllegalArgumentException("Transport is missing");
        }

        boolean isSelfCreatedExecutor = false;
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            isSelfCreatedExecutor = true;
        }

        server.init(duplicationMaxSize, coapTransport, scheduledExecutorService, isSelfCreatedExecutor,
                midSupplier, maxQueueSize, priority, maxIncomingBlockTransferSize,
                blockSize, delayedTransactionTimeout, duplicatedCoapMessageCallback);

        return server;
    }

    public CoapServerBuilder observerIdGenerator(ObservationIDGenerator observationIDGenerator) {
        server.setObservationIDGenerator(observationIDGenerator);
        return this;
    }

}
