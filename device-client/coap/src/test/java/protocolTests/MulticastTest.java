/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.testng.Assert.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Method;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.udp.DatagramChannelTransport;
import org.mbed.coap.transport.udp.MulticastSocketTransport;
import org.mbed.coap.utils.SimpleCoapResource;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class MulticastTest {

    @Test
//    @Ignore
    public void multicastConnection() throws IOException, CoapException {
        CoapServer server = CoapServerBuilder.newBuilder()
                .transport(new MulticastSocketTransport(new InetSocketAddress(0), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES)).build();
        server.addRequestHandler("/multicast", new SimpleCoapResource(
                "multicast"));
        // server.setMulticastGroup(InetAddress.getByName("FF02::1"));
        server.start();

        int port = server.getLocalSocketAddress().getPort();
        InetSocketAddress address = new InetSocketAddress(
                MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES, port);
        // InetSocketAddress address = new
        // InetSocketAddress("fe80:0:0:0:f0f1:7af6:3111:b7a6", 61619);

        CoapServer cnnServer = CoapServerBuilder.newBuilder()
                .transport(new MulticastSocketTransport(new InetSocketAddress(0), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES))
                .timeout(new SingleTimeout(1000000)).build();
        cnnServer.start();

        // multicast request
        CoapClient cnn = CoapClientBuilder.clientFor(address, cnnServer);
        CoapPacket msg = cnn.resource("/multicast").sync().get();
        assertEquals("multicast", msg.getPayloadString());

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
        msg = cnn2.resource("/multicast").sync().get();
        assertEquals("multicast", msg.getPayloadString());

        cnn2.close();
        cnnServer.stop();

        server.stop();
    }

    @Test(enabled = false)
    public void multicastRequest() throws IOException, CoapException {
        CoapServer server = CoapServerBuilder.newBuilder().transport(61619).build();
        server.addRequestHandler("/multicast", new SimpleCoapResource(
                "multicast"));
        server.start();

        CoapPacket coap = new CoapPacket(null);
        coap.setMethod(Method.GET);
        coap.headers().setUriPath("/multicast");

        // fe80:0:0:0:f0f1:7af6:3111:b7a6
        // InetSocketAddress addr = new InetSocketAddress("FF02::1", 61619);
        InetSocketAddress addr = new InetSocketAddress(
                "fe80:0:0:0:f0f1:7af6:3111:b7a6", 61619);
        DatagramPacket reqDatagram = new DatagramPacket(coap.toByteArray(),
                coap.toByteArray().length, addr);

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

    @Test(enabled = false)
    public void multicastTest() throws IOException {

        CoapServer server = CoapServerBuilder.newBuilder().transport(new DatagramChannelTransport(new InetSocketAddress("::1", 61619))).build();
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
