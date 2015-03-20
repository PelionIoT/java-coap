/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.mbed.coap.BlockSize;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.udp.DatagramChannelTransport;

/**
 * @author szymon
 */
public class CoapServerBuilder {

    private final CoapServerObserve server;
    private int duplicationMaxSize = 10000;

    CoapServerBuilder() {
        server = new CoapServerObserve();
    }

    public static CoapServerBuilder newBuilder() {
        return new CoapServerBuilder();
    }

    public CoapServerBuilder transport(TransportConnector transportConnector) {
        server.setTransportConnector(transportConnector);
        return this;
    }

    public CoapServerBuilder transport(int port) {
        server.setTransportConnector(new DatagramChannelTransport(new InetSocketAddress(port)));
        return this;
    }

    public CoapServerBuilder executor(Executor executor) {
        server.setExecutor(executor);
        return this;
    }

    public CoapServerBuilder scheduledExecutor(ScheduledExecutorService scheduledExecutorService) {
        server.setScheduledExecutor(scheduledExecutorService);
        return this;
    }

    public CoapServerBuilder context(CoapIdContext context) {
        server.setCoapIdContext(context);
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

    public CoapServerBuilder errorCallback(CoapErrorCallback errorCallback) {
        server.setErrorCallback(errorCallback);
        return this;
    }

    /**
     * Sets maximum number or requests to be kept for duplication detection.
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
        if (server.getTransport() == null) {
            server.setTransportConnector(DatagramChannelTransport.ofRandomPort());
        }
        server.init(duplicationMaxSize);
        return server;
    }

    public CoapServerBuilder observerIdGenerator(CoapServerObserve.ObservationIDGenerator observationIDGenerator) {
        server.setObservationIDGenerator(observationIDGenerator);
        return this;
    }
}
