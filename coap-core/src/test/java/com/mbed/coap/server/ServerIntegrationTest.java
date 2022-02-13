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
package com.mbed.coap.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class ServerIntegrationTest {

    CoapServer server = null;
    private int SERVER_PORT;

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(0).build();
        server.addRequestHandler("/test/1", new ReadOnlyCoapResource("Dziala", "simple", -1));
        server.addRequestHandler("/test2", new TestResource());
        server.addRequestHandler(CoapConstants.WELL_KNOWN_CORE, server.getResourceLinkResource());
        //server.addRequestHandler("/bigResource", new BigResource() );
        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();

    }

    @AfterEach
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

        request.headers().setAccept(MediaTypes.CT_APPLICATION_JSON);

        assertEquals(Code.C406_NOT_ACCEPTABLE, cnn.makeRequest(request).get().getCode());
        cnn.stop();
    }

    @Test
    public void removeRequestHandlerTest() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).start();

        CoapHandler hdlr = new ReadOnlyCoapResource("TEST");
        srv.addRequestHandler("/test", hdlr);
        CoapHandler hdlr2 = new ReadOnlyCoapResource("TEST2");
        srv.addRequestHandler("/test2", hdlr2);

        CoapClient client = CoapClientBuilder.newBuilder(srv.getLocalSocketAddress().getPort()).build();
        assertEquals("TEST", client.resource("/test").sync().get().getPayloadString());

        srv.removeRequestHandler(hdlr);
        assertEquals(Code.C404_NOT_FOUND, client.resource("/test").sync().get().getCode());

        srv.removeRequestHandler(mock(CoapHandler.class));

        srv.stop();
    }

    @Test
    public void resourceListTest() throws IOException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).build();
        srv.addRequestHandler("/test/1", new ReadOnlyCoapResource("TEST"));
        srv.start();

        List<LinkFormat> links = srv.getResourceLinks();
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/1", links.get(0).getUri());

        //add handler
        srv.addRequestHandler("/test/2", new ReadOnlyCoapResource("TEST2"));
        srv.addRequestHandler("/test/3", CoapExchange::sendResponse);

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
        assertEquals(0, pingResp.getPayload().size());
        assertTrue(pingResp.getToken().isEmpty());
    }

    private static class TestResource extends CoapResource {

        private String payload = "Dziala2";
        private short contentType = MediaTypes.CT_TEXT_PLAIN;

        @Override
        public void get(CoapExchange exchange) {
            if (exchange.getRequestHeaders().getAccept() != null) {
                boolean isFound = false;
                if (exchange.getRequestHeaders().getAccept() == contentType) {
                    isFound = true;
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
