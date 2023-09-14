/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
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
        return new DatagramSocketTransport(0);
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

        } finally {
            trans.stop();
        }
    }

    @Test
    public void initializeWithProvidedDatagramSocket() throws Exception {

        DatagramSocketAdapter udpSocket = new DatagramSocketAdapter(0);
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(udpSocket, null);

        datagramSocketTransport.start(mock(CoapReceiver.class));
        assertTrue(udpSocket.isBound());
        assertFalse(udpSocket.isClosed());

        assertEquals(udpSocket.getLocalPort(), datagramSocketTransport.getLocalSocketAddress().getPort());

        datagramSocketTransport.stop();
        assertTrue(udpSocket.isClosed());
    }

    @Test
    public void continueReadingWhenAfterReadingTimeout() throws Exception {
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(new InetSocketAddress(0), mock(Executor.class));

        //start
        datagramSocketTransport.start(mock(CoapReceiver.class));
        assertTrue(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));
        assertTrue(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));

        //stop
        datagramSocketTransport.stop();
        assertFalse(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));
    }
}
