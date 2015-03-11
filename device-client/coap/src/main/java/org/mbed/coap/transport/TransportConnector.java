/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Interface for transport layer.
 *
 * @author szymon
 */
public interface TransportConnector {

    /**
     * Starts connector.
     *
     * @param transReceiver transport receiver
     * @throws IOException io exception
     */
    void start(TransportReceiver transReceiver) throws IOException;

    /**
     * Stops UDP connector, removes binding.
     */
    void stop();

    /**
     * Sends specified data to destination address.
     *
     * @param data data
     * @param len length
     * @param destinationAddress destination address
     * @param transContext transport context
     * @throws IOException io exception
     */
    void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException;

    /**
     * Returns socket address that this connector is bound on.
     *
     * @return bound socket address
     */
    InetSocketAddress getLocalSocketAddress();
}
