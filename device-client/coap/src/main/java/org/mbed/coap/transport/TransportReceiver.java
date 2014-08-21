/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 *
 * @author szymon
 */
public interface TransportReceiver {
    
    /**
     * Handle data just received from transport layer.
     *
     * @param adr source address
     * @param buffer packet raw data
     * @param transportContext transport context
     */
    void onReceive(InetSocketAddress adr, ByteBuffer buffer, TransportContext transportContext);
}
