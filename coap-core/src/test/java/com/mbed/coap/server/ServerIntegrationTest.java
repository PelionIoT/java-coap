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

import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


public class ServerIntegrationTest {

    CoapServer server = null;
    private int SERVER_PORT;

    @BeforeEach
    public void setUp() throws IOException {

        TestResource testResource = new TestResource();
        final Service<CoapRequest, CoapResponse> route = RouterService.builder()
                .get("/test/1", req ->
                        completedFuture(CoapResponse.ok("Dziala", MediaTypes.CT_TEXT_PLAIN))
                )
                .get("/test2", testResource)
                .put("/test2", testResource)
                .post("/test2", testResource)
                .delete("/test2", testResource)
                .get(CoapConstants.WELL_KNOWN_CORE, req ->
                        completedFuture(CoapResponse.ok("<test/1>,<test2>", MediaTypes.CT_APPLICATION_LINK__FORMAT))
                )
                .build();

        server = CoapServerBuilder.newBuilder().transport(0).route(route).build();

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
    public void wellKnownResourcesTest() throws IOException, CoapException, ParseException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();
        CoapPacket msg = client.resource(CoapConstants.WELL_KNOWN_CORE).sync().get();

        assertNotNull(msg);
        LinkFormat[] links = LinkFormatBuilder.parseList(msg.getPayloadString());
        assertEquals(2, links.length);
    }

    @Test
    @Disabled
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

    private static class TestResource implements Service<CoapRequest, CoapResponse> {

        private Opaque payload = of("Dziala2");
        private short contentType = MediaTypes.CT_TEXT_PLAIN;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest request) {
            switch (request.getMethod()) {
                case GET:
                    return completedFuture(get(request));
                case POST:
                    return failedFuture(new CoapCodeException(Code.C400_BAD_REQUEST));
                case PUT:
                    return completedFuture(put(request));
                case DELETE:
                    return completedFuture(delete());
                default:
                    return failedFuture(new RuntimeException());
            }
        }

        public CoapResponse get(CoapRequest request) {
            if (request.options().getAccept() != null) {
                boolean isFound = false;
                if (request.options().getAccept() == contentType) {
                    isFound = true;
                }

                if (!isFound) {
                    //did not found accepted content type
                    return CoapResponse.of(Code.C406_NOT_ACCEPTABLE);
                }
            }

            return CoapResponse.ok(payload, contentType);
        }

        public CoapResponse put(CoapRequest request) {
            payload = request.getPayload();
            contentType = request.options().getContentFormat();
            return CoapResponse.of(Code.C204_CHANGED);
        }

        public CoapResponse delete() {
            payload = EMPTY;
            contentType = 0;
            return CoapResponse.of(Code.C202_DELETED);
        }

    }
}
