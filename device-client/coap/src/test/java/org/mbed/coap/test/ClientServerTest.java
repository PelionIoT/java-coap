package org.mbed.coap.test;

import org.mbed.coap.server.CoapServerBuilder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Future;
import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapMessage;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.CoapUtils;
import org.mbed.coap.CoapUtils.PacketDropping;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.udp.MulticastSocketTransport;
import org.mbed.coap.utils.FutureCallbackAdapter;
import org.mbed.coap.utils.SimpleCoapResource;
import org.mbed.coap.utils.SyncCallback;

/**
 *
 * @author szymon
 */
public class ClientServerTest {

    private CoapServer server = null;
    private int SERVER_PORT;

    @Before
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().build();
        server.addRequestHandler("/test/1", new SimpleCoapResource("Dziala"));
        server.addRequestHandler("/resource*", new SimpleCoapResource("Prefix dziala"));
        server.addRequestHandler("/", new SimpleCoapResource("Shortest path"));
        server.useCriticalOptionTest(false);
        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void simpleRequest() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCustomHeader() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put(74, new byte[]{1, 2, 3});
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCriticalCustomHeader() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        server.useCriticalOptionTest(true);
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put(71, new byte[]{1, 2, 3});
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);
        assertEquals(Code.C402_BAD_OPTION, callback.get().getCode());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCriticalCustomHeader2() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        server.useCriticalOptionTest(false);
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put((byte) 71, new byte[]{1, 2, 3});
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestToShortestPath() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/");
        request.setMessageId(1648);
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        cnn.makeRequest(request, callback);
        assertEquals("Shortest path", callback.getResponse().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequest2() throws Exception {
        try (CoapClient client = CoapClientBuilder.newBuilder().target(SERVER_PORT).build()) {

            Future<CoapPacket> coapResp = client.resource("/test/1").get();
            assertEquals("Dziala", coapResp.get().getPayloadString());
        }
    }

    @Test
    public void simpleRequest3() throws Exception {
        try (CoapClient client = CoapClientBuilder.newBuilder().target(SERVER_PORT).build()) {

            Future<CoapPacket> coapResp = client.resource("/resource/123").get();
            assertEquals("Prefix dziala", coapResp.get().getPayloadString());

            coapResp = client.resource("/test/1/tre").get();
            assertEquals(Code.C404_NOT_FOUND, coapResp.get().getCode());
        }
    }

    @Test
    public void simpleIPv6Request() throws CoapException, UnknownHostException, IOException {
        InetAddress adr = InetAddress.getByName("::1");

        CoapServer ipv6Server = CoapServerBuilder.newBuilder().transport(0).build();
        ipv6Server.addRequestHandler("/resource", new SimpleCoapResource("1234qwerty"));
        ipv6Server.start();

        try (CoapClient client = CoapClientBuilder.newBuilder(new InetSocketAddress(adr, ipv6Server.getLocalSocketAddress().getPort())).build()) {

            CoapMessage coapResp = client.resource("/resource").sync().get();
            assertEquals("1234qwerty", coapResp.getPayloadString());

        }
        ipv6Server.stop();
    }

    @Test
    public void duplicateTest() throws Exception {
        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(3000);

        CoapPacket cpRequest = new CoapPacket(Method.GET, MessageType.Confirmable, "/test/1", null);
        cpRequest.setMessageId(4321);
        DatagramPacket packet = new DatagramPacket(cpRequest.toByteArray(), cpRequest.toByteArray().length, InetAddress.getLocalHost(), SERVER_PORT);
        DatagramPacket recPacket = new DatagramPacket(new byte[1024], 1024);
        DatagramPacket recPacket2 = new DatagramPacket(new byte[1024], 1024);
        datagramSocket.send(packet);
        datagramSocket.receive(recPacket);

        //send duplicate
        Thread.sleep(20);
        datagramSocket.send(packet);
        datagramSocket.receive(recPacket2);
        datagramSocket.close();
        assertArrayEquals(recPacket.getData(), recPacket2.getData());

    }

    @Test
    public void requestWithPacketLost() throws CoapException, UnknownHostException, IOException {
        try (CoapClient cnn = CoapClientBuilder.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT)).build()) {

            final SimpleCoapResource res = new SimpleCoapResource("Not dropped");
            server.addRequestHandler("/dropable", res);

            //will drop only first packet
            server.setPacketDropping(new PacketDropping() {

                private boolean dropped = false;

                @Override
                public boolean drop() {
                    if (dropped) {
                        return false;
                    } else {
                        dropped = true;
                        res.setResourceBody("dropped");
                        return true;
                    }
                }
            });

            CoapMessage resp = cnn.resource("/dropable").sync().get();
            assertEquals("dropped", resp.getPayloadString());
        }
    }

    @Test
    public void simpleRequest4() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.start();

        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        CoapClient client = CoapClientBuilder.clientFor(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT), cnn);
        client.resource("/test/1").maxAge(2635593050L).get(callback);
        assertEquals("Dziala", callback.getResponse().getPayloadString());
        cnn.stop();
    }

    @Test
    public void reusePortSocketImpl() throws IOException, CoapException {
        TransportConnector udpConnector = new MulticastSocketTransport(new InetSocketAddress(0), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES); //new UDPMulticastConnector(61601, UDPMulticastConnector.MCAST_LINKLOCAL_ALLNODES);
        CoapServer srv = CoapServerBuilder.newBuilder().transport(udpConnector).build();
        srv.addRequestHandler("/test", new SimpleCoapResource("TTEESSTT"));
        srv.start();
        final int port = srv.getLocalSocketAddress().getPort();

        CoapClient cnn = CoapClientBuilder.newBuilder(port).build();
        assertEquals("TTEESSTT", cnn.resource("/test").sync().get().getPayloadString());

        srv.stop();

        TransportConnector udpConnector2 = new MulticastSocketTransport(new InetSocketAddress(port), MulticastSocketTransport.MCAST_LINKLOCAL_ALLNODES);
        CoapServer srv2 = CoapServerBuilder.newBuilder().transport(udpConnector2).build();
        srv2.addRequestHandler("/test", new SimpleCoapResource("TTEESSTT2"));
        srv2.start();

        assertEquals("TTEESSTT2", cnn.resource("/test").sync().get().getPayloadString());

        srv2.stop();
    }

    @Test
    public void simpleRequest5() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(61601)).build();
        srv.addRequestHandler("/temp", new SimpleCoapResource("23 C"));
        srv.start();

        try (CoapClient cnn = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(61601)).transport(InMemoryTransport.create(0)).build()) {
            assertEquals("23 C", cnn.resource("/temp").sync().get().getPayloadString());
        }
        srv.stop();
    }

    @Test
    public void simpleRequestWithUnknownCriticalOptionHeader() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(61601)).build();
        srv.addRequestHandler("/temp", new SimpleCoapResource("23 C"));
        srv.start();

        try (CoapClient client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(61601)).transport(new InMemoryTransport()).build()) {
            assertEquals(Code.C402_BAD_OPTION, client.resource("/temp").header(123, "dupa".getBytes()).sync().get().getCode());
        }
        srv.stop();
    }

    @Test(expected = java.lang.IllegalStateException.class)
    public void stopNonRunningServer() {
        CoapServer srv = CoapServerBuilder.newBuilder().build();
        srv.stop();
    }

    @Test(expected = java.lang.IllegalStateException.class)
    public void startRunningServer() throws IOException {
        CoapServer srv = CoapServerBuilder.newBuilder().build();
        srv.start();
        srv.start();
    }

    @Test
    public void testRequestWithPacketDelay() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().build();
        cnn.setPacketDelay(new CoapUtils.AvgPacketDelay(100));
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test(expected = org.mbed.coap.exception.CoapTimeoutException.class)
    public void testRequestWithPacketDropping() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(CoapConstants.DEFAULT_PORT)).build();
        srv.addRequestHandler("/test", new SimpleCoapResource("TEST"));
        srv.setPacketDropping(new CoapUtils.ProbPacketDropping((byte) 100));
        srv.start();

        CoapClient cnn = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(CoapConstants.DEFAULT_PORT))
                .transport(InMemoryTransport.create()).timeout(new SingleTimeout(100)).build();

        assertNotNull(cnn.resource("/test").sync().get());
    }

    @Test(expected = NullPointerException.class)
    public void testMakeRequestWithNullCallback() throws CoapException {
        server.makeRequest(new CoapPacket(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testMakeRequestWithNullAddress() throws CoapException {
        server.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", null), new FutureCallbackAdapter<CoapPacket>());
    }

    @Test(expected = NullPointerException.class)
    public void testMakeRequestNullRequest() throws CoapException {
        server.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", null), new FutureCallbackAdapter<CoapPacket>());
    }
}
