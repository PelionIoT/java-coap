/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.linkformat.LinkFormatBuilder;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MediaTypes;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.InMemoryTransport;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.SimpleCoapResource;

/**
 * @author szymon
 */
public class ServerTest {

    CoapServer server = null;
    private int SERVER_PORT;

    @Before
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(0, Executors.newCachedThreadPool()).build();
        server.addRequestHandler("/test/1", new SimpleCoapResource("Dziala", "simple"));
        server.addRequestHandler("/test2", new TestResource());
        server.addRequestHandler(CoapConstants.WELL_KNOWN_CORE, server.getResourceLinkResource());
        //server.addRequestHandler("/bigResource", new BigResource() );
        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();

    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void resourceManipulationTest() throws CoapException, IOException {
        //Connection cnn = new Connection(Inet4Address.getLocalHost());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();

        //Getting resources
        assertEquals("Dziala", client.resource("/test/1").get().join().getPayloadString());
        //assertEquals("Dziala2", cnn.get("/test2").getPayloadString());

        //posting to a resource with error
        assertEquals(Code.C405_METHOD_NOT_ALLOWED, client.resource("/test/1").sync().post().getCode());
        assertEquals(Code.C400_BAD_REQUEST, client.resource("/test2").sync().post().getCode());

        //getting non-existing resource
        assertEquals(Code.C404_NOT_FOUND, client.resource("/do-not-exist").sync().get().getCode());

        CoapPacket msg = client.resource("/.well-known/core").sync().get();
        assertNotNull(msg);

        client.close();
        //assertEquals("Test are running in same VM, change forkMode:always", ClientServerTest.staticTest, "start");
    }

    @Test
    public void requestWithAccept() throws UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test2");
        request.setMessageId(1647);

        short[] acceptList = {MediaTypes.CT_APPLICATION_JSON};
        request.headers().setAccept(acceptList);

        assertEquals(Code.C406_NOT_ACCEPTABLE, cnn.makeRequest(request).get().getCode());
        cnn.stop();
    }

    @Test
    public void requestWithAccept2() throws UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test2");
        request.setMessageId(1647);

        short[] acceptList = {MediaTypes.CT_APPLICATION_JSON, MediaTypes.CT_APPLICATION_XML, MediaTypes.CT_TEXT_PLAIN};
        request.headers().setAccept(acceptList);

        assertEquals(Code.C205_CONTENT, cnn.makeRequest(request).get().getCode());
        cnn.stop();
    }

    @Test
    public void removeRequestHandlerTest() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).build();
        srv.start();
        CoapHandler hdlr = new SimpleCoapResource("TEST");
        srv.addRequestHandler("/test", hdlr);

        CoapClient client = CoapClientBuilder.newBuilder(srv.getLocalSocketAddress().getPort()).build();
        assertEquals("TEST", client.resource("/test").sync().get().getPayloadString());

        srv.removeRequestHandler(hdlr);
        assertEquals(Code.C404_NOT_FOUND, client.resource("/test").sync().get().getCode());

        srv.stop();
    }

    @Test
    public void resourceListTest() throws IOException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).build();
        srv.addRequestHandler("/test/1", new SimpleCoapResource("TEST"));
        srv.start();

        List<LinkFormat> links = srv.getResourceLinks();
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/1", links.get(0).getUri());

        //add handler
        srv.addRequestHandler("/test/2", new SimpleCoapResource("TEST2"));
        srv.addRequestHandler("/test/3", new SimpleCoapResource("TEST3"));

        links = srv.getResourceLinks();
        assertNotNull(links);
        assertEquals(3, links.size());
    }

    @Test
    public void wellKnownResourcesTest() throws IOException, CoapException, ParseException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();
        CoapPacket msg = client.resource(CoapConstants.WELL_KNOWN_CORE).sync().get();

        assertNotNull(msg);
        LinkFormat[] links = LinkFormatBuilder.parseList(msg.getPayloadString());
        assertEquals(2, links.length);
    }

    @Test
    public void wellKnownResourcesFilterTest() throws IOException, CoapException, ParseException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).timeout(new SingleTimeout(100000)).build();
        CoapPacket msg = client.resource(CoapConstants.WELL_KNOWN_CORE).query("rt", "simple").sync().get();

        assertNotNull(msg);
        assertNotNull(msg.getPayloadString());
        LinkFormat[] links = LinkFormatBuilder.parseList(msg.getPayloadString());
        assertEquals(1, links.length);
    }

    @Test
    public void errorCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        server.setErrorCallback(new CoapErrorCallback() {
            @Override
            public void parserError(byte[] packet, CoapException exception) {
                latch.countDown();
            }

            @Override
            public void duplicated(CoapPacket request) {
            }
        });

        DatagramSocket client = new DatagramSocket();
        byte[] content = "kacsa".getBytes();
        DatagramPacket packet = new DatagramPacket(content, content.length, InetAddress.getLocalHost(), SERVER_PORT);
        client.send(packet);
        client.close();

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void bufferSizeTest() throws IOException, CoapException {
        int messageSize = 2000;
        //UDPConnectorChannel udpConnector1 = new UDPConnectorChannel(new InetSocketAddress(0));
        InMemoryTransport udpConnector1 = new InMemoryTransport(61616);
        udpConnector1.setBufferSize(messageSize + 100);
        TestResource testResource = new TestResource();

        CoapServer srv1 = CoapServerBuilder.newBuilder().transport(udpConnector1).build();
        srv1.addRequestHandler("/test", testResource);
        srv1.start();

        CoapClient client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(61616)).transport(InMemoryTransport.create()).build();

        client.resource("/test").payload(new byte[messageSize]).sync().put();
        assertEquals(messageSize, testResource.payload.length());
        srv1.stop();
    }

    @Test
    public void sendPing() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();
        CoapPacket pingResp = client.ping().get();

        assertEquals(MessageType.Reset, pingResp.getMessageType());
        assertNull(pingResp.getCode());
        assertEquals(0, pingResp.getPayload().length);
        assertEquals(0, pingResp.getToken().length);
    }

    private static class TestResource extends CoapResource {

        private String payload = "Dziala2";
        private short contentType = MediaTypes.CT_TEXT_PLAIN;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            if (exchange.getRequestHeaders().getAccept() != null) {
                boolean isFound = false;
                for (Short ac : exchange.getRequestHeaders().getAccept()) {
                    if (ac == contentType) {
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    //did not found accepted content type
                    exchange.setResponseCode(Code.C406_NOT_ACCEPTABLE);
                    exchange.sendResponse();
                    return;
                }
            }

            exchange.setResponseBody(payload);
            exchange.getResponseHeaders().setContentFormat(contentType);
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.sendResponse();
        }

        @Override
        public void post(CoapExchange exchange) throws CoapCodeException {
            throw new CoapCodeException(Code.C400_BAD_REQUEST);
        }

        @Override
        public void put(CoapExchange exchange) throws CoapCodeException {
            payload = exchange.getRequestBodyString();
            contentType = exchange.getRequestHeaders().getContentFormat();
            exchange.setResponseCode(Code.C204_CHANGED);
            exchange.sendResponse();
        }

        @Override
        public void delete(CoapExchange exchange) throws CoapCodeException {
            payload = "";
            contentType = 0;
            exchange.setResponseCode(Code.C202_DELETED);
            exchange.sendResponse();
        }

    }
}
