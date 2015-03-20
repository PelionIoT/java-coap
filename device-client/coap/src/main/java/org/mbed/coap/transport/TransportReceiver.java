/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.net.InetSocketAddress;

/**
 * Transport receiver interface.
 *
 * @author szymon
 */
public interface TransportReceiver {

    /**
     * Handle data received from transport layer.
     * Implementation MUST not block.
     *
     * @param adr source address
     * @param data packet raw data
     * @param transportContext transport context
     */
    void onReceive(InetSocketAddress adr, byte[] data, TransportContext transportContext);

    /**
     * Connection with a remote has been closed. It is used only by transport that supports connection (like TCP).
     *
     * @param remoteAddress remote address
     */
    void onConnectionClosed(InetSocketAddress remoteAddress);

}
