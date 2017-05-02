/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests.utils;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.TransportConnector;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportReceiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by szymon
 */
public class TransportConnectorMock implements TransportConnector {

    private TransportReceiver transReceiver;
    private final Map<CoapPacket, CoapPacket[]> conversationMap = new LinkedHashMap<>(); //order is important
    private CoapPacket lastOutgoingMessage = null;

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
            CoapPacket[] resp = findResponse(request);
            if (resp != null) {
                for (CoapPacket r : resp) {
                    if (r != null) {
                        receive(r, destinationAddress);
                    }
                }
            }
            lastOutgoingMessage = request;
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

    public CoapPacket getLastOutgoingMessage() {
        return lastOutgoingMessage;
    }

    private CoapPacket[] findResponse(CoapPacket coapPacket) {
        Optional<CoapPacket> first = conversationMap.keySet().stream().findFirst();
        if (!first.isPresent() || !first.get().equals(coapPacket)) {
            return null;
        }
        return conversationMap.remove(first.get());
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

        public void thenNothing() {
            conversationMap.put(incomingPacket, null);
        }

        public void then(CoapPacket responsePacket) {
            conversationMap.put(incomingPacket, new CoapPacket[]{responsePacket});
        }

        public void then(CoapPacket responsePacket1, CoapPacket responsePacket2) {
            conversationMap.put(incomingPacket, new CoapPacket[]{responsePacket1, responsePacket2});
        }
    }
}
