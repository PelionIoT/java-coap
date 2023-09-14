/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package com.mbed.coap.server.internal;

import static org.junit.Assert.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.utils.CoapResource;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by olesmi01 on 11.12.2015.
 * Test for deduplication of error requests
 */
public class CoapServerDuplicateErrorsTest {
    private CountDownLatch latch;
    CoapServer server;
    MockCoapTransport serverTransport;

    @Before
    public void setUp() throws IOException {
        serverTransport = new MockCoapTransport();
        server = CoapServerBuilder.newBuilder().transport(serverTransport)
                .duplicatedCoapMessageCallback(
                        request -> {
                            if (latch != null) {
                                latch.countDown();
                            }
                        })
                .build()
                .start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testNotFoundResourceCon_get() throws IOException, CoapException, InterruptedException {
        testIt(Method.GET, MessageType.Confirmable, 11, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.GET, MessageType.Confirmable, 13, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_put() throws IOException, CoapException, InterruptedException {
        testIt(Method.PUT, MessageType.Confirmable, 22, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.PUT, MessageType.Confirmable, 25, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_post() throws IOException, CoapException, InterruptedException {
        testIt(Method.POST, MessageType.Confirmable, 33, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.POST, MessageType.Confirmable, 36, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_delete() throws IOException, CoapException, InterruptedException {
        testIt(Method.DELETE, MessageType.Confirmable, 44, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.DELETE, MessageType.Confirmable, 47, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceNon_get() throws IOException, CoapException, InterruptedException {
        testIt(Method.GET, MessageType.NonConfirmable, 55, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.GET, MessageType.NonConfirmable, 58, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_put() throws IOException, CoapException, InterruptedException {
        testIt(Method.PUT, MessageType.NonConfirmable, 66, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.PUT, MessageType.NonConfirmable, 69, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_post() throws IOException, CoapException, InterruptedException {
        testIt(Method.POST, MessageType.NonConfirmable, 77, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.POST, MessageType.NonConfirmable, 70, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_delete() throws IOException, CoapException, InterruptedException {
        testIt(Method.DELETE, MessageType.NonConfirmable, 88, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.DELETE, MessageType.NonConfirmable, 81, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testFailedResourceCon_get() throws InterruptedException, CoapException, IOException {
        server.addRequestHandler("/failed", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new NullPointerException("failed");
            }
        });

        testIt(Method.GET, MessageType.Confirmable, 110, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.Acknowledgement, true);
        testIt(Method.GET, MessageType.Confirmable, 114, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.Acknowledgement, true);
    }

    @Test
    public void testFailedResourceNon_get() throws InterruptedException, CoapException, IOException {
        server.addRequestHandler("/failed", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new NullPointerException("failed");
            }
        });

        testIt(Method.GET, MessageType.NonConfirmable, 150, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.NonConfirmable, false);
        testIt(Method.GET, MessageType.NonConfirmable, 154, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNoMethodResourceCon_get() throws InterruptedException, CoapException, IOException {
        server.addRequestHandler("/failed", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new NullPointerException("failed");
            }
        });

        testIt(Method.POST, MessageType.Confirmable, 110, "/failed", Code.C405_METHOD_NOT_ALLOWED, MessageType.Acknowledgement, true);
        testIt(Method.PUT, MessageType.Confirmable, 114, "/failed", Code.C405_METHOD_NOT_ALLOWED, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNoMethodResourceNon_get() throws InterruptedException, CoapException, IOException {
        server.addRequestHandler("/failed", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new NullPointerException("failed");
            }
        });

        testIt(Method.POST, MessageType.NonConfirmable, 150, "/failed", Code.C405_METHOD_NOT_ALLOWED, MessageType.NonConfirmable, false);
        testIt(Method.PUT, MessageType.NonConfirmable, 154, "/failed", Code.C405_METHOD_NOT_ALLOWED, MessageType.NonConfirmable, false);
    }

    private void testIt(Method reqMethod, MessageType reqType, int reqMsgId, String reqUri, Code expectedRespCode, MessageType expectedRespType, boolean reqAndRespMsgIdMatch) throws IOException, CoapException, InterruptedException {
        CoapPacket req = new CoapPacket(reqMethod, reqType, reqUri, server.getLocalSocketAddress());
        req.setMessageId(reqMsgId);
        testIt(req, expectedRespCode, expectedRespType, reqAndRespMsgIdMatch);
    }

    private void testIt(CoapPacket req, Code expectedRespCode, MessageType expectedRespType, boolean reqAndRespMsgIdMatch) throws IOException, CoapException, InterruptedException {
        latch = new CountDownLatch(1);

        serverTransport.receive(req);
        CoapPacket resp1 = serverTransport.sentPackets.poll(10, TimeUnit.SECONDS);
        System.out.println(resp1);

        serverTransport.receive(req);
        CoapPacket resp2 = serverTransport.sentPackets.poll(1, TimeUnit.SECONDS);
        System.out.println(resp2);
        assertEquals(resp1, resp2);
        if (reqAndRespMsgIdMatch) {
            assertEquals(req.getMessageId(), resp1.getMessageId());
        } else {
            assertNotEquals(req.getMessageId(), resp1.getMessageId());
        }
        assertEquals(expectedRespType, resp1.getMessageType());
        assertEquals(expectedRespCode, resp1.getCode());
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

}
