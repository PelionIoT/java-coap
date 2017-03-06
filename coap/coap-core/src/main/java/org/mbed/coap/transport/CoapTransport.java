/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;

/**
 * Created by szymon
 */
public interface CoapTransport {
    void start(CoapReceiver coapReceiver) throws IOException;

    void stop();

    void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

    InetSocketAddress getLocalSocketAddress();
}
