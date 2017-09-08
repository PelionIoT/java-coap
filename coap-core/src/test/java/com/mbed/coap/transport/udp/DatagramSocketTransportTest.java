/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.transport.udp;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;

/**
 * @author szymon
 */
public class DatagramSocketTransportTest {

    public static final CoapPacket COAP_PACKET = CoapPacketBuilder.newCoapPacket().get().uriPath("/test").mid(1).build();

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
                trans.sendPacket0(COAP_PACKET, new InetSocketAddress(5683), TransportContext.NULL);
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
        DatagramSocketTransport trans = new DatagramSocketTransport(new InetSocketAddress(0), Runnable::run, false);
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
