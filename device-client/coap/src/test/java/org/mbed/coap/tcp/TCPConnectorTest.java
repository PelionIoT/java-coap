package org.mbed.coap.tcp;

import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapMessage;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.Method;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.tcp.TCPClientConnector;
import org.mbed.coap.tcp.TCPServerConnector;
import org.mbed.coap.test.StubCoapServer;
import org.mbed.coap.transmission.CoapTimeout;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.utils.CoapCallback;
import org.mbed.coap.utils.SimpleCoapResource;
import org.mbed.coap.utils.SyncCallback;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author KALLE
 */
public class TCPConnectorTest {

    private static final int defaultServerPort = 61639;
    private static final String defaultPath = "/tcptest";
    private static final String defaultResourceValue = "dupa";

    @Test
    public void simpleTCPRequest() throws Exception {
        CoapServer server = startServer(0, null, null);
        int srvPort = server.getLocalSocketAddress().getPort();
        System.out.println("server port " + srvPort);
        TCPClientConnector tcpCnn = new TCPClientConnector();
        CoapClient cnn = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), srvPort)).transport(tcpCnn).timeout(1000).build();
        assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
        cnn.close();

        stopServer(server);
    }

    @Test
    public void testZebra() throws Exception {
        final CoapServer theServer = startServer(0, "/tcptest", null);
        //theServer.setResponseTimeout(new CoapUtils.SingleTimeout(1000));
        int nspPort = theServer.getLocalSocketAddress().getPort();

        InetSocketAddress nspAddress = new InetSocketAddress(InetAddress.getLocalHost(), nspPort);

        final TCPClientConnector clientConnector = new TCPClientConnector();
        CoapServer client = CoapServer.newBuilder().transport(clientConnector).timeout(new SingleTimeout(1000)).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(nspAddress);
        assertTrue(socketChannel.finishConnect());
        System.out.println("NSP address: " + nspAddress);
        //assertTrue( socketChannel.finishConnect() );

        client.setBlockSize(BlockSize.S_1024);
        client.start();
        clientConnector.addSocketChannel(nspAddress, socketChannel);
        // after this can start monitoring

        // making request example;
        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/tcptest");
        request.setAddress(nspAddress);
        SyncCallback<CoapPacket> syncResp = new SyncCallback<CoapPacket>();

        System.out.println("Send -----");
        client.makeRequest(request, syncResp);
        assertNotNull(syncResp.getResponse());

        // also can add request handler for client
        client.addRequestHandler("/test", new SimpleCoapResource("ds"));

        // zebra socket tester
//        Thread runnable = new Thread() {
//            public boolean isSocketConnected() {
//                if (clientConnector == null) {
//                    return true; // lets retry later.
//                }
//
//                System.out.println("Our created socket=" + socketChannel.toString());
//                System.out.println("socketChannel.isOpen()" + socketChannel.isOpen());
//                System.out.println("socketChannel.isConnected()" + socketChannel.isConnected());
//                return socketChannel != null && socketChannel.isOpen() && socketChannel.isConnected();
//            }
//
//            @Override
//            public void run() {
//                int counter = 0;
//                int threshold = Integer.valueOf(1);
//
//                while (true) {
//
//                    try {
//                        TimeUnit.SECONDS.sleep(Long.valueOf(1));
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//
//                    if (!isSocketConnected()) {
//                        counter++; //only one thread is reading and writing here so AtomicInteger is not required.
//                        System.out.println("We are NOT connected to the cloud, socket had dropped or something. Adding counter={}");
//                    } else {
//                        System.out.println("We are connected to the cloud. TimeInMillis={}" + System.currentTimeMillis());
//                    }
//
//
//                    if (counter > threshold) {
//                        String clientConnectorString = clientConnector.toString();
//                        counter = 0;
//                        System.out.println("Recreating connection thereby creating coapserver. Old TCPCLientConnector={}, New TCPClientConnector={}" + clientConnectorString + clientConnector.toString());
//                    }
//                }
//            }
//        };
//        runnable.start();
        //Thread.sleep(5000);
        CoapPacket request2 = new CoapPacket();
        request2.setMethod(Method.GET);
        request2.headers().setUriPath("/test");
        request2.setAddress(new InetSocketAddress(InetAddress.getLocalHost(), ((InetSocketAddress) socketChannel.getLocalAddress()).getPort()));
        theServer.makeRequest(request2, new CoapCallback() {
            @Override
            public void callException(Exception ex) {
                fail("called callback exception");
            }

            @Override
            public void call(CoapPacket t) {
                System.out.println("response packet " + t);
            }
        });
        Thread.sleep(2000);
        Socket rawSocket = null;
        try {
            rawSocket = new Socket(nspAddress.getHostName(), nspPort);
            OutputStream rawStream = rawSocket.getOutputStream();
            rawStream.write(new byte[]{-40, 60});
            rawStream.close();
        } finally {
            if (rawSocket != null) {
                rawSocket.close();
            }
        }
        Socket rawSocket2 = null;
        try {
            rawSocket2 = new Socket(nspAddress.getHostName(), nspPort);
            OutputStream rawStream2 = rawSocket2.getOutputStream();
            rawStream2.write(new byte[]{5});
            rawStream2.close();
        } finally {
            if (rawSocket2 != null) {
                rawSocket2.close();
            }
        }
        Thread.sleep(1000);
        final List<String> ok = new ArrayList<String>();
        theServer.makeRequest(request2, new CoapCallback() {
            @Override
            public void callException(Exception ex) {
                fail("called callback exception");
            }

            @Override
            public void call(CoapPacket t) {
                System.out.println("response packet " + t);
                ok.add("ok");
            }
        });
        Thread.sleep(500);
        if (ok.isEmpty()) {
            fail("not received response");
        }
        Thread.sleep(500);
        stopServer(theServer);
    }

    @Test
    public void testZebra2() throws Throwable {
        final CoapServer theServer = startServer(0, "/tcptest", null, 32000);
        //theServer.setResponseTimeout(new CoapUtils.SingleTimeout(1000));
        int nspPort = theServer.getLocalSocketAddress().getPort();

        InetSocketAddress nspAddress = new InetSocketAddress(InetAddress.getLocalHost(), nspPort);

        final TCPClientConnector clientConnector = new TCPClientConnector(32000);
        final TCPClientConnector tcpCnn2 = new TCPClientConnector();
        final CoapServer client = CoapServer.newBuilder().transport(clientConnector).timeout(new SingleTimeout(4000)).build();
        final CoapServer client2 = CoapServer.newBuilder().transport(tcpCnn2).timeout(new SingleTimeout(4000)).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(nspAddress);
        assertTrue(socketChannel.finishConnect());

        final SocketChannel socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketChannel2.connect(nspAddress);
        assertTrue(socketChannel2.finishConnect());
        System.out.println("NSP address: " + nspAddress);
        //assertTrue( socketChannel.finishConnect() );

        client.setBlockSize(null);
        client.start();
        clientConnector.addSocketChannel(nspAddress, socketChannel);

        client2.setBlockSize(null);
        client2.start();
        tcpCnn2.addSocketChannel(nspAddress, socketChannel2);
        // after this can start monitoring

        // making request example;
        final CoapPacket request = new CoapPacket();
        request.setMethod(Method.POST);
        request.headers().setUriPath("/tcptest");
        String longPayloadOneKilo = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
        String longPayload30Kilo = "";
        for (int i = 0; i < 30; i++) {
            longPayload30Kilo = longPayload30Kilo + longPayloadOneKilo;
        }
        request.setPayload(longPayload30Kilo);
        request.setAddress(nspAddress);
        final SyncCallback<CoapPacket> bigSyncResp = new SyncCallback<CoapPacket>();
        final SyncCallback<CoapPacket> syncResp = new SyncCallback<CoapPacket>();
        final SyncCallback<CoapPacket> syncResp2 = new SyncCallback<CoapPacket>();
        final SyncCallback<CoapPacket> syncResp3 = new SyncCallback<CoapPacket>();

        final CoapPacket requestC2 = new CoapPacket();
        requestC2.setMethod(Method.GET);
        requestC2.headers().setUriPath("/tcptest");
        requestC2.setAddress(nspAddress);

        System.out.println("Send -----");
        client.makeRequest(request, bigSyncResp);
        assertNotNull(bigSyncResp.getResponse());

        // also can add request handler for client
        client.addRequestHandler("/test", new SimpleCoapResource("ds"));

        // zebra socket tester
        Thread runnable = new Thread() {
            public boolean isSocketConnected() {
                System.out.println("Our created socket=" + socketChannel.toString());
                System.out.println("socketChannel.isOpen()" + socketChannel.isOpen());
                System.out.println("socketChannel.isConnected()" + socketChannel.isConnected());
                return socketChannel.isOpen() && socketChannel.isConnected();
            }

            @Override
            public void run() {
                int counter = 0;
                int threshold = Integer.valueOf(1);

                while (true) {

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (!isSocketConnected()) {
                        counter++; //only one thread is reading and writing here so AtomicInteger is not required.
                        System.out.println("We are NOT connected to the cloud, socket had dropped or something. Adding counter={}");
                    } else {
                        System.out.println("We are connected to the cloud. TimeInMillis={}" + System.currentTimeMillis());
                    }

                    if (counter > threshold) {
                        String clientConnectorString = clientConnector.toString();
                        counter = 0;
//                        theServer.stop();

                        //server = null;
                        //instance.start();
                        System.out.println("Recreating connection thereby creating coapserver. Old TCPCLientConnector={}, New TCPClientConnector={}" + clientConnectorString + clientConnector.toString());

                    }
                }
            }
        };
        runnable.start();
        //Thread.sleep(5000);
        final CoapPacket request2 = new CoapPacket();
        request2.setMethod(Method.GET);
        request2.headers().setUriPath("/test");
        request2.setAddress(new InetSocketAddress(InetAddress.getLocalHost(), ((InetSocketAddress) socketChannel.getLocalAddress()).getPort()));
//
        final CoapPacket request222 = new CoapPacket();
        request222.setMethod(Method.GET);
        request222.headers().setUriPath("/test");
        request222.setAddress(new InetSocketAddress(InetAddress.getLocalHost(), ((InetSocketAddress) socketChannel2.getLocalAddress()).getPort()));

        theServer.makeRequest(request2, new CoapCallback() {
            @Override
            public void callException(Exception ex) {
//                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void call(CoapPacket t) {
//                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        final List<Boolean> failures = new ArrayList<Boolean>(0);
        Thread cSender = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Sending");
                    for (int i = 0; i < 100; i++) {
//                        if (i % 100 == 0) {
//                        System.out.println("Client " + i);
//                        }
                        client.makeRequest(request, bigSyncResp);
                        CoapPacket resp = bigSyncResp.getAndClear();
//                        System.out.println(resp.getCode());
                        if (resp.getCode() != Code.C405_METHOD_NOT_ALLOWED) {
                            failures.add(Boolean.FALSE);
                        }
                        Thread.sleep(20);
//                        if ((i + 1) % 100 == 0) {
//                            System.out.println("shutting down client");
//                            client.stop();
//                            return;
//                        }
                    }
                } catch (Exception e) {
                    failures.add(Boolean.FALSE);
                    e.printStackTrace();
                }
            }
        };
        cSender.start();

        Thread cSender2 = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Sending");
                    for (int i = 0; i < 100; i++) {
//                        if (i % 100 == 0) {
//                            System.out.println("Client2 " + i);
//                        }
                        client2.makeRequest(requestC2, syncResp);
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    failures.add(Boolean.FALSE);
                    e.printStackTrace();
                }

            }
        };
        cSender2.start();

        Thread sSender = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Sending");
                    for (int i = 0; i < 100; i++) {
//                        if (i % 100 == 0) {
//                            System.out.println("Server " + i);
//                        }
                        theServer.makeRequest(request2, syncResp2);
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    failures.add(Boolean.FALSE);
                    e.printStackTrace();
                }
            }
        };
        sSender.start();

        Thread sSender2 = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Sending");
                    for (int i = 0; i < 100; i++) {
//                        if (i % 100 == 0) {
//                            System.out.println("Server2 " + i);
//                        }
                        theServer.makeRequest(request222, syncResp3);
                        if (syncResp.getAndClear() == null) {
                            failures.add(Boolean.FALSE);
                        }
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    failures.add(Boolean.FALSE);
                    e.printStackTrace();
                }
            }
        };
        sSender2.start();

        Thread.sleep(5000);
        Integer failCount = 0;
        if (!failures.isEmpty()) {
            for (Boolean b : failures) {
                if (b != null && !b) {
                    failCount++;
                }
            }
            assertEquals("failures: " + failCount, (Integer) 0, failCount);
        } else {
            System.out.println("no failures");
        }
        stopServer(theServer);
    }

    //    @Test
//    public void multipleTCPRequest() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
//        CoapServer server = startServer(0, null, null);
//        int srvPort = server.getLocalSocketAddress().getPort();
//        System.out.println("server port " + srvPort);
//        TCPClientConnector tcpCnn = new TCPClientConnector();
//        CoapConnection cnn = CoapConnection.create(new InetSocketAddress(Inet4Address.getLocalHost(), srvPort), (UDPConnectorWorker) tcpCnn);
//        cnn.setResponseTimeout(new CoapUtils.SingleTimeout(1000));
//        assertEquals(defaultResourceValue, cnn.get(defaultPath).getPayloadString());
//        cnn.closeConnection();
//
//        stopServer(server);
//    }
    @Test
    public void multipleTCPRequests() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer server = startServer(0, null, null);
        int srvPort = server.getLocalSocketAddress().getPort();
        System.out.println("server port" + srvPort);

        TCPClientConnector tcpCnn = new TCPClientConnector();
        TCPClientConnector tcpCnn2 = new TCPClientConnector();
        CoapClient cnn = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), srvPort)).transport(tcpCnn).build();
        CoapClient cnn2 = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), srvPort)).transport(tcpCnn2).build();
        assertEquals("dupa", cnn.resource("/tcptest").sync().get().getPayloadString());
        assertEquals("dupa", cnn2.resource("/tcptest").sync().get().getPayloadString());
        assertEquals("dupa", cnn.resource("/tcptest").sync().get().getPayloadString());
        assertEquals("dupa", cnn2.resource("/tcptest").sync().get().getPayloadString());

        assertEquals("dupa", cnn2.resource("/tcptest").sync().get().getPayloadString());
        assertEquals("dupa", cnn.resource("/tcptest").sync().get().getPayloadString());
//        assertEquals("dupa", cnn2.get("/tcptest").getPayloadString());
        cnn.close();
        cnn2.close();

        stopServer(server);
    }

    @Test(expected = CoapException.class)
    public void notStartedServer() throws CoapException, UnknownHostException, IOException, Exception {
        System.out.println("not started server test");
        TCPClientConnector tcpCnn = new TCPClientConnector();
        CoapClient cnn = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), defaultServerPort)).transport(tcpCnn).timeout(500).build();
        assertEquals(defaultResourceValue, cnn.resource("/dupa_no_server").sync().get().getPayloadString());
//        cnn.closeConnection();
    }

    @Test
    public void serverStartStop() throws Exception {
        CoapServer server = startServer(0, null, null);
        int srvPort = server.getLocalSocketAddress().getPort();
        System.out.println("server port" + srvPort);
        TCPClientConnector tcpCnn = new TCPClientConnector();
        CoapClient cnn = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), srvPort)).transport(tcpCnn).timeout(500).build();
        assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
        stopServer(server);
        try {
            assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
            fail("must fail!");
        } catch (Exception e) {
        }
        server = startServer(srvPort, null, null);
        assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
        stopServer(server);
        cnn.close();
    }

    @Test
    public void serverStartStopTimeoutsWithPortAndServerSend() throws Exception {
        CoapServer server = startServer(0, null, null, 1024, new SingleTimeout(2000));
        int port = server.getLocalSocketAddress().getPort();
        TCPClientConnector tcpCnn = new TCPClientConnector();

        CoapClient cnn = CoapClient.newBuilder(new InetSocketAddress(InetAddress.getLocalHost(), port)).transport(tcpCnn).timeout(160).build();

        assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
        stopServer(server);
        try {
            assertEquals(defaultResourceValue, cnn.resource(defaultPath).sync().get().getPayloadString());
            fail("must fail!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        server = startServer(port, null, null, 1024, new SingleTimeout(8000));
        CoapMessage response = cnn.resource(defaultPath).sync().get();
        assertEquals(defaultResourceValue, response.getPayloadString());

        stopServer(server);
        cnn.close();
    }

    @Test
    public void clientCloseTest() throws UnknownHostException, IOException, CoapException, InterruptedException {
        TCPServerConnector tcpTransport = new TCPServerConnector(new InetSocketAddress("127.0.0.1", 0));
        StubCoapServer stubServer = new StubCoapServer(tcpTransport, 1000l);
        stubServer.start();

        StubCoapServer stubClient = new StubCoapServer(new TCPClientConnector(), 1000l);
        stubClient.start();

        assertNotNull(stubClient.client(stubServer.getLocalPort()).resource("/path1").sync().get());

        System.out.println("---");
        System.out.println("SERVER PORT: " + stubServer.getLocalPort());
        System.out.println("CLIENT PORT: " + stubServer.verify("/path1").getAddress().getPort());
        assertNotNull(stubServer.client(stubServer.verify("/path1").getAddress().getPort()).resource("/").sync().get());

        stubClient.stop();
        Thread.sleep(50);
        try {
            stubServer.client(stubServer.verify("/path1").getAddress().getPort()).resource("/").sync().get();
        } catch (CoapException ex) {
            assertTrue(ex.getCause() instanceof IOException);
        }
        stubServer.stop();
    }

    private static CoapServer startServer(Integer port, String path, String resourceValue) throws Exception {
        return startServer(port, path, resourceValue, 1024);
    }

    private static CoapServer startServer(Integer port, String path, String resourceValue, int maxSize) throws Exception {
        return startServer(port, path, resourceValue, maxSize, null);
    }

    private static CoapServer startServer(Integer port, String path, String resourceValue, int maxSize, TransmissionTimeout transTimeout) throws Exception {
        if (port == null) {
            port = defaultServerPort;
        }
        if (path == null) {
            path = defaultPath;
        }
        if (resourceValue == null) {
            resourceValue = defaultResourceValue;
        }
        if (transTimeout == null) {
            transTimeout = new CoapTimeout();
        }
        TCPServerConnector receiver = new TCPServerConnector(new InetSocketAddress(InetAddress.getLocalHost(), port), maxSize);
        CoapServer tcpServer = CoapServer.newBuilder().timeout(transTimeout).transport(receiver).build();
        tcpServer.addRequestHandler(path, new SimpleCoapResource(resourceValue));
        tcpServer.start();
        return tcpServer;
    }

    private static void stopServer(CoapServer server) {
        try {
            server.stop();
        } catch (Exception e) {
            System.out.println("error on server stop " + e);
        }
    }
}
