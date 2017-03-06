/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport.udp;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import org.junit.Test;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transport.CoapReceiver;
import org.mbed.coap.transport.TransportContext;
import protocolTests.utils.CoapPacketBuilder;

/**
 * @author szymon
 */
public class DatagramSocketTransportTest {

    public static final CoapPacket COAP_PACKET = CoapPacketBuilder.newCoapPacket().get().uriPath("/test").build();

    @Test
    public void clientServerTest() throws Exception {
        CoapServer server = CoapServerBuilder.newBuilder().transport(createDatagramSocketTransport()).build();
        server.start();

        CoapClient client = CoapClientBuilder.newBuilder(server.getLocalSocketAddress().getPort()).transport(createDatagramSocketTransport()).build();

        assertNotNull(client.ping().get());
        server.stop();
    }

    private static DatagramSocketTransport createDatagramSocketTransport() {
        return new DatagramSocketTransport(0, Runnable::run);
    }

    @Test
    public void initializingWithStateException() throws IOException {
        DatagramSocketTransport trans = createDatagramSocketTransport();
        try {
            try {
                trans.sendPacket(COAP_PACKET, new InetSocketAddress(5683), TransportContext.NULL);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }

            trans.start(mock(CoapReceiver.class));

            try {
                trans.setReuseAddress(true);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }
            try {
                trans.setSocketBufferSize(1234);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }
        } finally {
            trans.stop();
        }
    }

    @Test
    public void initializeWithParameters() throws Exception {
        DatagramSocketTransport trans = new DatagramSocketTransport(new InetSocketAddress(0), Runnable::run);
        trans.setReuseAddress(false);
        trans.setSocketBufferSize(12345);
        trans.start(mock(CoapReceiver.class));

        assertTrue(trans.getSocket().isBound());
        assertFalse(trans.getSocket().isClosed());
        assertEquals(12345, trans.getSocket().getSendBufferSize());
        assertFalse(trans.getSocket().getReuseAddress());

        trans.stop();
        assertTrue(trans.getSocket().isClosed());
    }

    @Test
    public void reopenSamePort() throws IOException {
        DatagramSocketTransport trans = createDatagramSocketTransport();
        trans.start(mock(CoapReceiver.class));
        assertFalse(trans.getSocket().isClosed());
        int localPort = trans.getLocalSocketAddress().getPort();
        trans.stop();
        assertTrue(trans.getSocket().isClosed());

        //bind again to same port
        trans = new DatagramSocketTransport(localPort, Runnable::run);

        trans.start(mock(CoapReceiver.class));
        assertFalse(trans.getSocket().isClosed());
        System.out.println(trans.getLocalSocketAddress());
        trans.stop();
    }

    @Test
    public void sendingWithTrafficClass() throws Exception {
        final DatagramSocket socket = spy(new QoSDatagramSocket(new InetSocketAddress(0)));
        DatagramSocketTransport trans = spy(createDatagramSocketTransport());
        when(trans.createSocket()).thenReturn(socket);

        trans.start(mock(CoapReceiver.class));

        trans.sendPacket(COAP_PACKET, new InetSocketAddress("::1", 5683), TrafficClassTransportContext.create(TrafficClassTransportContext.HIGH, TransportContext.NULL));
        verify(socket).setTrafficClass(TrafficClassTransportContext.HIGH);
        verify(socket).setTrafficClass(0);

        reset(socket);
        trans.sendPacket(COAP_PACKET, new InetSocketAddress("::1", 5683), TrafficClassTransportContext.create(89, TransportContext.NULL));
        verify(socket).setTrafficClass(89);
        verify(socket).setTrafficClass(0);

        //no traffic class
        reset(socket);
        trans.sendPacket(COAP_PACKET, new InetSocketAddress("::1", 5683), null);
        verify(socket, never()).setTrafficClass(anyInt());

        trans.stop();

    }

    @Test
    public void sendingCoapWithTrafficClass() throws Exception {
        final DatagramSocket socket = spy(new QoSDatagramSocket(new InetSocketAddress(0)));
        DatagramSocketTransport trans = spy(createDatagramSocketTransport());
        when(trans.createSocket()).thenReturn(socket);

        CoapClient client = CoapClientBuilder.newBuilder(5683).transport(trans).timeout(10000).build();

        client.resource("/test").context(TrafficClassTransportContext.create(TrafficClassTransportContext.HIGH, TransportContext.NULL)).get();
        verify(socket).setTrafficClass(TrafficClassTransportContext.HIGH);
        verify(socket).setTrafficClass(0);

        reset(socket);
        client.resource("/test").get();
        verify(socket, never()).setTrafficClass(anyInt());

        client.close();
    }
}
