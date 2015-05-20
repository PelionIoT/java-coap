/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.net.InetSocketAddress;
import org.mbed.coap.exception.ReceiveException;

/**
 * Transport receiver interface.
 *
 * @author szymon
 */
public interface TransportReceiver {

    /**
     * Handle data received from transport layer. Implementation MUST not block.
     *
     * @param adr source address
     * @param data packet raw data
     * @param transportContext transport context
     *
     * @throws ReceiveException for anticipated errors in handling the receive, not to be regarded as bugs (so e.g. no
     * stacktrace is needed in the catch)
     */
    void onReceive(InetSocketAddress adr, byte[] data, TransportContext transportContext) throws ReceiveException;

    /**
     * Connection with a remote has been closed. It is used only by transport that supports connection (like TCP).
     *
     * @param remoteAddress remote address
     */
    void onConnectionClosed(InetSocketAddress remoteAddress);

}
