/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by szymon
 */
public class MockCoapTransport implements CoapTransport {

    private volatile CoapReceiver coapReceiver = null;
    public final BlockingQueue<CoapPacket> sentPackets = new ArrayBlockingQueue<CoapPacket>(100);

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        this.coapReceiver = coapReceiver;
    }

    @Override
    public void stop() {
        coapReceiver = null;
    }

    @Override
    public void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        sentPackets.add(coapPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return new InetSocketAddress(5683);
    }

    public void receive(CoapPacket packet) {
        coapReceiver.handle(packet, TransportContext.NULL);
    }
}
