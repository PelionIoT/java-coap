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
package com.mbed.coap.server;

import static org.junit.Assert.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.CoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author szymon
 */
public class CoapServerDuplicateTest {

    static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("localhost", 6666);
    CoapServer server;
    final AtomicInteger duplicated = new AtomicInteger(0);
    MockCoapTransport serverTransport;

    @Before
    public void setUp() throws IOException {
        duplicated.set(0);
        serverTransport = new MockCoapTransport();

        server = CoapServerBuilder.newCoapServer(serverTransport);
        server.setDuplicatedCoapMessageCallback(request -> duplicated.incrementAndGet());


        server.addRequestHandler("/test", new CoapResource() {

            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new IllegalStateException();
            }

            @Override
            public void put(CoapExchange exchange) throws CoapCodeException {
                exchange.setResponseBody("dupa");
                exchange.sendResponse();
            }
        });

        server.addRequestHandler("/test-non", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                exchange.setResponseBody("dupa2");
                exchange.getResponse().setMessageType(MessageType.NonConfirmable);
                exchange.sendResponse();
            }
        });


        server.start();
    }

    @After
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
        assertEquals("MsgID should be same", resp1.getMessageId(), resp2.getMessageId());
        assertEquals(resp1, resp2);
        assertEquals("MsgID should be equal to request MID", resp1.getMessageId(), req.getMessageId());
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
            assertNotEquals("response message ID for NON response should not be 0 more than once", resp1.getMessageId(), 0);
        }
        //        System.out.println(resp1);
        assertEquals("dupa2", resp1.getPayloadString());

        //repeated request
        serverTransport.receive(req);
        CoapPacket resp2 = serverTransport.sentPackets.poll(1, TimeUnit.SECONDS);

        assertEquals("dupa2", resp2.getPayloadString());
        assertTrue(duplicated.get() == 1);
        assertEquals("MsgID should be same", resp1.getMessageId(), resp2.getMessageId());
        assertEquals(resp1, resp2);
        // can fail with probability 1/0xFFFF
        assertNotEquals("Request and response MID should be different", resp1.getMessageId(), req.getMessageId());
    }

    @Test
    public void duplicateRequestWithoutErrorCallbackHandler() throws Exception {
        CoapServer server = CoapServerBuilder.newBuilder().transport(new InMemoryCoapTransport(0)).build();
        server.start();

        CoapPacket req = new CoapPacket(Method.PUT, MessageType.Confirmable, "/test", REMOTE_ADDRESS);
        server.handle(req, TransportContext.NULL);
        server.handle(req, TransportContext.NULL);

        server.stop();
    }

    @Test
    public void testDuplicateRequestNotProcessed() throws IOException, CoapException, InterruptedException {
        final CountDownLatch delayResourceLatch = new CountDownLatch(1);
        server.addRequestHandler("/test-delay", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                new Thread(() -> {
                    try {
                        delayResourceLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    exchange.setResponseBody("dupa3");
                    exchange.sendResponse();
                }).start();
            }
        });


        CoapPacket req = new CoapPacket(Method.GET, MessageType.Confirmable, "/test-delay", REMOTE_ADDRESS);

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
        delayResourceLatch.countDown();

        resp = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        assertEquals("dupa3", resp.getPayloadString());
        //assertNull(coapServer.verifyPUT("/test"));
        assertTrue("unexpected messages", serverTransport.sentPackets.isEmpty());
        assertTrue(duplicated.get() == 1);
    }

    @Test
    public void testDuplicateNotification() throws IOException, CoapException, InterruptedException {
        final AtomicBoolean notificationArrived = new AtomicBoolean(false);
        server.setObservationHandler(new ObservationHandler() {
            @Override
            public boolean hasObservation(byte[] token) {
                return true;
            }

            @Override
            public void call(CoapExchange coapExchange) {
                notificationArrived.set(true);
                coapExchange.sendResponse();
            }

            @Override
            public void callException(Exception ex) {
                ex.printStackTrace();
            }
        });

        CoapPacket notif = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, REMOTE_ADDRESS);
        notif.setMessageId(12);
        notif.setToken(DataConvertingUtility.convertVariableUInt(1234));
        notif.headers().setObserve(1);
        notif.setPayload("dupa2");

        serverTransport.receive(notif);
        CoapPacket resp = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        assertEquals(MessageType.Acknowledgement, resp.getMessageType());
        assertTrue(notificationArrived.get());
        assertTrue(duplicated.get() == 0);

        //repeated request
        //coapServer.reset();
        notificationArrived.set(false);
        serverTransport.receive(notif);
        resp = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        assertEquals(MessageType.Acknowledgement, resp.getMessageType());
        assertFalse("received notification from retransmission", notificationArrived.get());
        assertTrue(duplicated.get() == 1);
    }
}
