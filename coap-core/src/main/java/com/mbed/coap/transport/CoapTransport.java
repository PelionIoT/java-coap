/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by szymon
 */
public interface CoapTransport {
    void start(CoapReceiver coapReceiver) throws IOException;

    void stop();

    void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

    InetSocketAddress getLocalSocketAddress();
}
