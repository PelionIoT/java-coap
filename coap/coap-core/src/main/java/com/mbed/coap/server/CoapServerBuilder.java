/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author szymon
 */
public class CoapServerBuilder {

    private final CoapServerObserve server;
    private int duplicationMaxSize = 10000;
    private CoapTransport coapTransport;

    CoapServerBuilder() {
        server = new CoapServerObserve();
    }

    public static CoapServerBuilder newBuilder() {
        return new CoapServerBuilder();
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
        server.setScheduledExecutor(scheduledExecutorService);
        return this;
    }

    public CoapServerBuilder midSupplier(MessageIdSupplier context) {
        server.setMidSupplier(context);
        return this;
    }

    public CoapServerBuilder timeout(TransmissionTimeout timeout) {
        server.setTransmissionTimeout(timeout);
        return this;
    }

    public CoapServerBuilder delayedTimeout(int delayedTransactionTimeout) {
        server.setDelayedTransactionTimeout(delayedTransactionTimeout);
        return this;
    }

    public CoapServerBuilder blockSize(BlockSize blockSize) {
        server.setBlockSize(blockSize);
        return this;
    }

    public CoapServerBuilder maxIncomingBlockTransferSize(int size) {
        server.setMaxIncomingBlockTransferSize(size);
        return this;
    }

    public CoapServerBuilder errorCallback(CoapErrorCallback errorCallback) {
        server.setErrorCallback(errorCallback);
        return this;
    }

    public CoapServerBuilder defaultTransactionQueuePriority(CoapTransaction.Priority priority) {
        server.setDefaultCoapTransactionPriority(priority);
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

    public CoapServer build() {
        if (coapTransport == null) {
            throw new IllegalArgumentException("Transport is missing");
        }

        server.setCoapTransporter(coapTransport);
        server.init(duplicationMaxSize);
        return server;
    }

    public CoapServerBuilder observerIdGenerator(CoapServerObserve.ObservationIDGenerator observationIDGenerator) {
        server.setObservationIDGenerator(observationIDGenerator);
        return this;
    }

}
