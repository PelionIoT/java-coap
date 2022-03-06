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

import static com.mbed.coap.server.CoapServerBuilder.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.internal.MockCoapTransport;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class CoapServerDuplicateTest {

    static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("localhost", 6666);
    CoapServer server;
    final AtomicInteger duplicated = new AtomicInteger(0);
    MockCoapTransport serverTransport;
    private final CompletableFuture<CoapResponse> delayResource = new CompletableFuture<>();

    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .put("/test", req ->
                    completedFuture(CoapResponse.ok("dupa"))
            )
            .get("/test-non", req ->
                    completedFuture(CoapResponse.ok("dupa2"))
            )
            .get("/test-delay", req ->
                    delayResource
            )
            .build();

    @BeforeEach
    public void setUp() throws IOException {
        duplicated.set(0);
        serverTransport = new MockCoapTransport();

        server = newBuilder()
                .transport(serverTransport)
                .duplicateMsgCacheSize(100)
                .duplicatedCoapMessageCallback(request -> duplicated.incrementAndGet())
                .route(route)
                .build();

        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testDuplicateRequest_confirmable() throws IOException, CoapException, InterruptedException {
        CoapPacket req = new CoapPacket(Method.PUT, MessageType.Confirmable, "/test", REMOTE_ADDRESS);
        req.setMessageId(12);

        serverTransport.receive(req);

        CoapPacket resp1 = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        assertEquals("dupa", resp1.getPayloadString());

        //repeated request
        serverTransport.receive(req);
        CoapPacket resp2 = serverTransport.sentPackets.poll(1, TimeUnit.SECONDS);

        assertEquals("dupa", resp2.getPayloadString());
        assertTrue(duplicated.get() == 1);
        assertEquals(resp1.getMessageId(), resp2.getMessageId(), "MsgID should be same");
        assertEquals(resp1, resp2);
        assertEquals(resp1.getMessageId(), req.getMessageId(), "MsgID should be equal to request MID");
    }

    @Test()
    public void testDuplicateRequest_nonConfirmable() throws IOException, CoapException, InterruptedException {
        CoapPacket req = new CoapPacket(Method.GET, MessageType.NonConfirmable, "/test-non", REMOTE_ADDRESS);
        req.setMessageId(33);

        serverTransport.receive(req);
        CoapPacket resp1 = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        if (resp1.getMessageId() == 0 || resp1.getMessageId() == req.getMessageId()) { //retry
            req.setMessageId(32);

            serverTransport.receive(req);
            resp1 = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
            assertNotEquals(resp1.getMessageId(), 0, "response message ID for NON response should not be 0 more than once");
        }
        //        System.out.println(resp1);
        assertEquals("dupa2", resp1.getPayloadString());

        //repeated request
        serverTransport.receive(req);
        CoapPacket resp2 = serverTransport.sentPackets.poll(1, TimeUnit.SECONDS);

        assertEquals("dupa2", resp2.getPayloadString());
        assertTrue(duplicated.get() == 1);
        assertEquals(resp1.getMessageId(), resp2.getMessageId(), "MsgID should be same");
        assertEquals(resp1, resp2);
        // can fail with probability 1/0xFFFF
        assertNotEquals(resp1.getMessageId(), req.getMessageId(), "Request and response MID should be different");
    }

    @Test
    public void duplicateRequestWithoutErrorCallbackHandler() throws Exception {
        CoapServer server = newBuilder().transport(new InMemoryCoapTransport(0)).build();
        server.start();

        CoapPacket req = new CoapPacket(Method.PUT, MessageType.Confirmable, "/test", REMOTE_ADDRESS);
        server.getCoapMessaging().handle(req, TransportContext.EMPTY);
        server.getCoapMessaging().handle(req, TransportContext.EMPTY);

        server.stop();
    }

    @Test
    public void testDuplicateRequestNotProcessed() throws IOException, CoapException, InterruptedException {
        CoapPacket req = new CoapPacket(Method.GET, MessageType.Confirmable, "/test-delay", REMOTE_ADDRESS);
        req.setMessageId(11);

        serverTransport.receive(req);

        CoapPacket resp = serverTransport.sentPackets.poll(10, TimeUnit.MILLISECONDS);
        assertNull(resp);

        //repeaded request
        System.out.println("second request");
        serverTransport.receive(req);
        resp = serverTransport.sentPackets.poll(10, TimeUnit.MILLISECONDS);
        assertNull(resp);

        //let response be send
        System.out.println("let response be send");
        delayResource.complete(CoapResponse.ok("dupa3"));

        resp = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        assertEquals("dupa3", resp.getPayloadString());
        //assertNull(coapServer.verifyPUT("/test"));
        assertTrue(serverTransport.sentPackets.isEmpty(), "unexpected messages");
        assertTrue(duplicated.get() == 1);
    }

}
