/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import static org.junit.Assert.*;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.SimpleCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
