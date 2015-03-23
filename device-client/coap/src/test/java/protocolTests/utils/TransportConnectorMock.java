/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package protocolTests.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;

/**
 * Created by szymon
 */
public class TransportConnectorMock implements TransportConnector {

    private TransportReceiver transReceiver;
    private final Map<CoapPacket, CoapPacket> conversationMap = new HashMap<>();

    @Override
    public void start(TransportReceiver transReceiver) throws IOException {
        this.transReceiver = transReceiver;
    }

    @Override
    public void stop() {

    }

    @Override
    public void send(final byte[] data, final int len, final InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        try {
            CoapPacket request = CoapPacket.read(data, len, null);
            CoapPacket resp = findResponse(request);
            if (resp != null) {
                receive(resp, destinationAddress);
            }
        } catch (CoapException e) {
            e.printStackTrace();
        }
    }

    public void receive(CoapPacket coapPacket, InetSocketAddress sourceAddress) {
        try {
            transReceiver.onReceive(sourceAddress, coapPacket.toByteArray(), null);
        } catch (CoapException e) {
            e.printStackTrace();
        }

    }

    private CoapPacket findResponse(CoapPacket coapPacket) {
        return conversationMap.get(coapPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return null;
    }

    public TransportConnectorMockTeacher when(CoapPacket incomingPacket) {
        return new TransportConnectorMockTeacher(incomingPacket);
    }

    public class TransportConnectorMockTeacher {
        private final CoapPacket incomingPacket;

        public TransportConnectorMockTeacher(CoapPacket incomingPacket) {
            this.incomingPacket = incomingPacket;
        }

        public void then(CoapPacket responsePacket) {
            conversationMap.put(incomingPacket, responsePacket);
        }
    }
}
