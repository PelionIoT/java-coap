/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.client;

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author szymon
 */
public final class CoapClientBuilder {

    private InetSocketAddress destination;
    private CoapServer coapServer;

    CoapClientBuilder() {
        // nothing to initialize
    }

    CoapClientBuilder(int localPort) {
        target(localPort);
    }

    CoapClientBuilder(InetSocketAddress destination) {
        target(destination);
    }

    /**
     * Creates CoAP client builder.
     *
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder() {
        return new CoapClientBuilder();
    }

    /**
     * Creates CoAP client builder with target on localhost.
     *
     * @param localPort local port number
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder(int localPort) {
        return new CoapClientBuilder(localPort);
    }

    /**
     * Creates CoAP client builder with target socket address.
     *
     * @param destination target address
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder(InetSocketAddress destination) {
        return new CoapClientBuilder(destination);
    }

    public static CoapClient clientFor(InetSocketAddress target, CoapServer server) {
        return new CoapClient(target, server);
    }

    public CoapClient build() throws IOException {
        getOrCreateServer().start();
        return new CoapClient(destination, coapServer);
    }

    public CoapClientBuilder target(InetSocketAddress destination) {
        this.destination = destination;
        return this;
    }

    public CoapClientBuilder target(int localPort) {
        try {
            this.destination = new InetSocketAddress(InetAddress.getLocalHost(), localPort);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public CoapClientBuilder transport(CoapTransport trans) {
        if (coapServer != null) {
            throw new IllegalStateException("Transport already initialized");
        }
        coapServer = CoapServerBuilder.newBuilder().transport(trans).build();
        return this;
    }

    public CoapClientBuilder transport(int bindingPort) {
        if (coapServer != null) {
            throw new IllegalStateException("Transport already initialized");
        }
        coapServer = CoapServerBuilder.newBuilder().transport(bindingPort).build();
        return this;
    }

    public CoapClientBuilder timeout(long singleTimeoutMili) {
        getOrCreateServer().setTransmissionTimeout(new SingleTimeout(singleTimeoutMili));
        return this;
    }

    public CoapClientBuilder timeout(TransmissionTimeout responseTimeout) {
        getOrCreateServer().setTransmissionTimeout(responseTimeout);
        return this;
    }

    public CoapClientBuilder blockSize(BlockSize blockSize) {
        getOrCreateServer().setBlockSize(blockSize);
        return this;
    }

    public CoapClientBuilder delayedTransTimeout(int delayedTransactionTimeout) {
        getOrCreateServer().setDelayedTransactionTimeout(delayedTransactionTimeout);
        return this;
    }

    public CoapClientBuilder maxIncomingBlockTransferSize(int maxSize) {
        getOrCreateServer().setMaxIncomingBlockTransferSize(maxSize);
        return this;
    }

    private CoapServer getOrCreateServer() {
        if (coapServer == null) {
            coapServer = CoapServerBuilder.newBuilder().transport(0).build();
        }
        return coapServer;
    }
}
