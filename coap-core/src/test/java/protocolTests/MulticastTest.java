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
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.transport.udp.MulticastSocketTransport;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author szymon
 */
public class MulticastTest {

    @Test
    //    @Ignore
    public void multicastConnection() throws IOException, CoapException {
        CoapServer server = CoapServerBuilder.newBuilder()
                .transport(new MulticastSocketTransport(new InetSocketAddress(0), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES, Runnable::run)).build();
        server.addRequestHandler("/multicast", new ReadOnlyCoapResource(
                "multicast"));
        // server.setMulticastGroup(InetAddress.getByName("FF02::1"));
        server.start();

        int port = server.getLocalSocketAddress().getPort();
        //        InetSocketAddress address = new InetSocketAddress(
        //                MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES, port);

        CoapServer cnnServer = CoapServerBuilder.newBuilder()
                .transport(new MulticastSocketTransport(new InetSocketAddress(0), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES, Runnable::run))
                .timeout(new SingleTimeout(1000000)).build();
        cnnServer.start();

        // multicast request
        //CoapClient cnn = CoapClientBuilder.clientFor(address, cnnServer);
        //CoapPacket msg = cnn.resource("/multicast").sync().get();
        //assertEquals("multicast", msg.getPayloadString());

        // IPv6 request
        CoapClient cnn3 = CoapClientBuilder.clientFor(new InetSocketAddress("::1", port), cnnServer);
        CoapPacket msg3 = cnn3.resource("/multicast").sync().get();
        assertEquals("multicast", msg3.getPayloadString());

        // IPv4 request
        CoapClient cnn4 = CoapClientBuilder.clientFor(new InetSocketAddress("127.0.0.1", port), cnnServer);
        CoapPacket msg4 = cnn4.resource("/multicast").sync().get();
        assertEquals("multicast", msg4.getPayloadString());

        // IPv4 request (using Datagram channel)
        CoapClient cnn2 = CoapClientBuilder.newBuilder(new InetSocketAddress("127.0.0.1", port)).build();
        CoapPacket msg = cnn2.resource("/multicast").sync().get();
        assertEquals("multicast", msg.getPayloadString());

        cnn2.close();
        cnnServer.stop();

        server.stop();
    }

    @Test
    @Ignore
    public void multicastRequest() throws IOException, CoapException {
        CoapServer server = CoapServerBuilder.newBuilder().transport(61619).build();
        server.addRequestHandler("/multicast", new ReadOnlyCoapResource(
                "multicast"));
        server.start();

        CoapPacket coap = new CoapPacket(null);
        coap.setMethod(Method.GET);
        coap.headers().setUriPath("/multicast");

        InetSocketAddress addr = new InetSocketAddress("FF02::1", 61619);
        //InetSocketAddress addr = new InetSocketAddress("fe80:0:0:0:f0f1:7af6:3111:b7a6", 61619);
        DatagramPacket reqDatagram = new DatagramPacket(coap.toByteArray(), coap.toByteArray().length, addr);

        DatagramSocket soc = null;
        DatagramPacket respDatagram;
        try {
            soc = new DatagramSocket(61620);
            soc.send(reqDatagram);
            respDatagram = new DatagramPacket(new byte[1024], 1024);
            soc.receive(respDatagram);
        } finally {
            if (soc != null) {
                soc.close();
            }
        }

        server.stop();
    }

    @Test
    @Ignore
    public void multicastTest() throws IOException {

        CoapServer server = CoapServerBuilder.newBuilder().transport(new DatagramSocketTransport(new InetSocketAddress("::1", 61619), Runnable::run)).build();
        server.start();

        DatagramSocket soc = null;
        MulticastSocket msoc = null;
        try {
            soc = new DatagramSocket(61620);
            DatagramPacket reqDatagram = new DatagramPacket(
                    "Wiadomosc".getBytes(), 9, new InetSocketAddress("FF02::1",
                    61619)
            );

            msoc = new MulticastSocket(61619);
            msoc.joinGroup(InetAddress.getByName("FF02::1"));
            DatagramPacket respDatagram = new DatagramPacket(new byte[100], 100);

            soc.send(reqDatagram);
            msoc.receive(respDatagram);

            msoc.send(respDatagram);
            soc.receive(reqDatagram);
        } finally {
            try {
                if (soc != null) {
                    soc.close();
                }
            } finally {
                if (msoc != null) {
                    msoc.close();
                }
            }
        }

    }
}
