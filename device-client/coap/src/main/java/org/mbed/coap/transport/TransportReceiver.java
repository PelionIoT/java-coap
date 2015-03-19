/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * @author szymon
 */
public interface TransportReceiver {

    /**
     * Handle data just received from transport layer.
     * Implementation MUST not block.
     *
     * @param adr source address
     * @param buffer packet raw data
     * @param transportContext transport context
     */
    void onReceive(InetSocketAddress adr, ByteBuffer buffer, TransportContext transportContext);

    /**
     * Connection with a remote has been closed. It is used only by transport that supports connection (like TCP).
     *
     * @param remoteAddress remote address
     */
    void onConnectionClosed(InetSocketAddress remoteAddress);

}
