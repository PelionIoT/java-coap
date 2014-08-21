package org.mbed.coap.tcp;

import org.mbed.coap.CoapMessage;
import org.mbed.coap.Code;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.tcp.SocketChannelConnector;
import org.mbed.coap.tcp.TCPServerConnector;
import org.mbed.coap.test.StubCoapServer;
import org.mbed.coap.transmission.CoapTimeout;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class SocketChannelConnectorTest {

    private StubCoapServer server;

    @Before
    public void setUp() throws IOException {
        TCPServerConnector serverConnector = new TCPServerConnector(new InetSocketAddress("localhost", 0));
        server = new StubCoapServer(serverConnector);
        server.start();
        server.when("/test").thenReturn("dupa");
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void successTest() throws IOException, CoapException {
        SocketChannelConnector socConnector = new SocketChannelConnector();
        CoapClient client = CoapClient.newBuilder(server.getAddress()).transport(socConnector).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(server.getAddress());
        assertTrue(socketChannel.finishConnect());
        socConnector.setSocketChannel(socketChannel);

        System.out.println("------");
        assertNotNull(client.resource("/test").sync().get());
        assertEquals(Code.C205_CONTENT, client.resource("/test").sync().get().getCode());

        client.close();
    }

    @Test(expected = IOException.class)
    public void differentDestinationAddress() throws IOException, Throwable {
        SocketChannelConnector socConnector = new SocketChannelConnector();
        CoapClient client = CoapClient.newBuilder(new InetSocketAddress("localhost", 1)).transport(socConnector).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(server.getAddress());
        assertTrue(socketChannel.finishConnect());
        socConnector.setSocketChannel(socketChannel);

        try {
            client.resource("/test").sync().get();
        } catch (CoapException ex) {
            throw ex.getCause();
        }

        client.close();
    }

    @Test(expected = IOException.class)
    public void noSocket() throws IOException, Throwable {
        SocketChannelConnector socConnector = new SocketChannelConnector();
        CoapClient client = CoapClient.newBuilder(1).transport(socConnector).build();

        try {
            client.resource("/test").sync().get();
        } catch (CoapException ex) {
            throw ex.getCause();
        }

        client.close();
    }

    @Test
    public void serverSendsFirst() throws IOException, CoapException, InterruptedException {
        SocketChannelConnector socConnector = new SocketChannelConnector();
        CoapClient client = CoapClient.newBuilder(server.getAddress()).transport(socConnector).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(server.getAddress());
        assertTrue(socketChannel.finishConnect());
        socConnector.setSocketChannel(socketChannel);

        System.out.println("------");

        CoapMessage clientResp = null;

        for (int i = 0; i < 100; i++) {
            try {
                clientResp = server.client(socConnector.getLocalSocketAddress().getPort()).resource("/fds").sync().get();
                break;
            } catch (CoapException ex) {
                //ignore
                System.out.println("Exception while server send [" + i + "]: " + ex.getMessage());
                Thread.sleep(10);
            }
        }
        assertNotNull(clientResp);
        assertEquals(Code.C404_NOT_FOUND, clientResp.getCode());

        assertNotNull(client.resource("/test").sync().get());

        client.close();
    }

    @Test(expected = IllegalStateException.class)
    public void setSocket_notConnected() throws IOException {
        SocketChannelConnector socConnector = new SocketChannelConnector();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socConnector.setSocketChannel(socketChannel);

    }

    @Test(expected = IllegalStateException.class)
    public void setSocket_notBlocking() throws IOException {
        SocketChannelConnector socConnector = new SocketChannelConnector();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(server.getAddress());
        assertTrue(socketChannel.finishConnect());
        socConnector.setSocketChannel(socketChannel);
    }

    @Test
    public void serverDisconets() throws IOException, CoapException {
        SocketChannelConnector socConnector = new SocketChannelConnector();
        CoapClient client = CoapClient.newBuilder(server.getAddress()).transport(socConnector)
                .timeout(new CoapTimeout(500)).build();

        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(true);
        socketChannel.connect(server.getAddress());
        assertTrue(socketChannel.finishConnect());
        socConnector.setSocketChannel(socketChannel);

        assertNotNull(client.resource("/test").sync().get());

        server.stop();
        server = null;
        //assertFalse(socketChannel.isConnected());
        System.out.println("------ stopped");

        try {
            client.resource("/test").sync().get();
            fail("CoapException expected");
        } catch (CoapException coapException) {
            //expected
        }
        System.out.println("------ second try");
        try {
            client.resource("/test").sync().get();
            fail("CoapException expected");
        } catch (CoapException coapException) {
            coapException.printStackTrace();
            //expected
        }

        client.close();
    }
}
