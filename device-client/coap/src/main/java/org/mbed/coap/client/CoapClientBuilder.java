/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.mbed.coap.BlockSize;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportConnector;

/**
 *
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

    public CoapClientBuilder transport(TransportConnector trans) {
        if (coapServer != null) {
            throw new IllegalStateException("Transport already initialized");
        }
        coapServer = CoapServer.newBuilder().transport(trans).build();
        return this;
    }

    public CoapClientBuilder transport(int bindingPort) {
        if (coapServer != null) {
            throw new IllegalStateException("Transport already initialized");
        }
        coapServer = CoapServer.newBuilder().transport(bindingPort).build();
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

    private CoapServer getOrCreateServer() {
        if (coapServer == null) {
            coapServer = CoapServer.newBuilder().transport(0).build();
        }
        return coapServer;
    }
}
