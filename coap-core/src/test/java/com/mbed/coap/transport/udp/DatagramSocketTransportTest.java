/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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

import static com.mbed.coap.transport.InMemoryCoapTransport.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;


public class DatagramSocketTransportTest {

    public static final CoapPacket COAP_PACKET = newCoapPacket(LOCAL_5683).get().uriPath("/test").mid(1).build();

    private static DatagramSocketTransport createDatagramSocketTransport() throws IOException {
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(0);
        datagramSocketTransport.start();
        return datagramSocketTransport;
    }

    @Test
    public void initializingWithStateException() throws IOException {
        DatagramSocketTransport trans = new DatagramSocketTransport(0);
        try {
            try {
                trans.sendPacket0(COAP_PACKET);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }

            trans.start();

        } finally {
            trans.stop();
        }
    }

    @Test
    public void initializeWithProvidedDatagramSocket() throws Exception {

        DatagramSocketAdapter udpSocket = new DatagramSocketAdapter(0);
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(udpSocket, null);

        datagramSocketTransport.start();
        assertTrue(udpSocket.isBound());
        assertFalse(udpSocket.isClosed());

        assertEquals(udpSocket.getLocalPort(), datagramSocketTransport.getLocalSocketAddress().getPort());

        datagramSocketTransport.stop();
        assertTrue(udpSocket.isClosed());
    }

    @Test
    public void sendAndReceive() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        DatagramSocketTransport trans1 = createDatagramSocketTransport();
        DatagramSocketTransport trans2 = createDatagramSocketTransport();
        InetSocketAddress trans2Addr = localAddressFrom(trans2.getLocalSocketAddress());

        // #1
        CompletableFuture<CoapPacket> receive = trans2.receive();
        assertFalse(receive.isDone());
        trans1.sendPacket(newCoapPacket(trans2Addr).get().uriPath("/test").mid(1).build());
        assertNotNull(receive.get(1, TimeUnit.SECONDS));

        // #2
        trans1.sendPacket(newCoapPacket(trans2Addr).get().uriPath("/test").mid(1).build());
        receive = trans2.receive();
        assertNotNull(receive.get(1, TimeUnit.SECONDS));

        // #5
        trans1.sendPacket(newCoapPacket(trans2Addr).get().uriPath("/test").mid(1).build());
        trans1.sendPacket(newCoapPacket(trans2Addr).get().uriPath("/test").mid(1).build());
        trans1.sendPacket(newCoapPacket(trans2Addr).get().uriPath("/test").mid(1).build());

        assertNotNull(trans2.receive().get(1, TimeUnit.SECONDS));
        assertNotNull(trans2.receive().get(1, TimeUnit.SECONDS));
        assertNotNull(trans2.receive().get(1, TimeUnit.SECONDS));

        // #4
        assertFalse(trans2.receive().isDone());

        trans1.stop();
        trans2.stop();
        assertThrows(Exception.class, trans2::receive);
    }
}
