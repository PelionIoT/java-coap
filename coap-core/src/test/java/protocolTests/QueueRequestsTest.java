/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package protocolTests;

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.transmission.RetransmissionBackOff.ofFixed;
import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protocolTests.utils.CoapPacketBuilder.LOCAL_5683;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.MockCoapTransport;


public class QueueRequestsTest {

    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private MockCoapTransport.MockClient server;
    private CoapClient client;

    @BeforeEach
    public void setUp() throws Exception {
        MockCoapTransport transport = new MockCoapTransport();

        client = CoapServer.builder().transport(transport)
                .midSupplier(new MessageIdSupplierImpl(0))
                .blockSize(BlockSize.S_32)
                .noDuplicateCheck()
                .queueMaxSize(2)
                .retransmission(ofFixed(ofMillis(500)))
                .buildClient(SERVER_ADDRESS);

        server = transport.client();
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldSendRequestsToADevice_singleRequest() throws Exception {
        //request
        CompletableFuture<CoapResponse> futResp = client.send(get("/path1"));

        server.verifyReceived();

        //send response
        server.send(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa"));

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldSendRequestsToADevice_isASequence_2_requests() throws Exception {
        //requests
        CompletableFuture<CoapResponse> futResp1 = client.send(get("/path1"));
        CompletableFuture<CoapResponse> futResp2 = client.send(get("/path2"));

        //only one request should be send
        server.verifyReceived();

        //send response
        server.send(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa1"));

        //second request should be send
        server.verifyReceived();

        //send response
        server.send(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2"));


        assertEquals("dupa1", futResp1.get().getPayloadString());
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }


    @Test
    public void shouldSendRequestsToADevice_isASequence_2_requests_with_block() throws Exception {
        CoapPacket blockResp1 = newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build();
        CoapPacket blockResp2 = newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build();

        //requests
        CompletableFuture<CoapResponse> futResp1 = client.send(get("/path1")); // makes req with msgId #1 and sends it
        CompletableFuture<CoapResponse> futResp2 = client.send(get("/path2")); // makes req with msgId #2 and queues it

        //#1 message - block1
        server.verifyReceived();

        server.send(blockResp1); // fetches and removes transaction #1, makes block#2 req with msgId #3 and sends it (#2 still in the queue)

        //#2 message - block2
        server.verifyReceived();
        server.send(blockResp2); // fetches and removes transaction #3, sends queued transaction #2

        assertEquals("123456789012345|dupa", futResp1.get().getPayloadString());

        //#2 message - request2
        server.verifyReceived();
        server.send(newCoapPacket(SERVER_ADDRESS).mid(3).ack(Code.C205_CONTENT).payload("dupa2"));

        //verify responses
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }


    @Test
    public void shouldQueueBlockTransferEvenQueueIsFull() throws Exception {
        CoapPacket blockResp1 = newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build();
        CoapPacket blockResp2 = newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build();

        //requests
        CompletableFuture<CoapResponse> futResp1 = client.send(get("/block")); // adds first request
        CompletableFuture<CoapResponse> futResp2 = client.send(get("/path2")); // adds second request

        //#1 message - block1 - causes adding to queue extra message from block transfer for a short time (until initial
        // transaction is removed
        server.verifyReceived();
        server.send(blockResp1);

        //#1 message - block2
        server.verifyReceived();
        server.send(blockResp2);


        //#2 message - request2
        server.verifyReceived();
        server.send(newCoapPacket(SERVER_ADDRESS).mid(3).ack(Code.C205_CONTENT).payload("dupa2"));


        //verify responses
        assertEquals("123456789012345|dupa", futResp1.get().getPayloadString());
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }

    @Test
    public void test_shouldNotDeleteInactiveTransaction() throws Exception {
        //requests
        CompletableFuture<CoapResponse> futResp1 = client.send(get("/path1"));
        CompletableFuture<CoapResponse> futResp2 = client.send(get("/path2"));

        //only one request should be send
        server.verifyReceived();

        //send response for queued inactive transaction
        server.send(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2"));

        server.nothingReceived();
        assertFalse(futResp1.isDone());
        assertFalse(futResp2.isDone());

        //send response for first transaction
        server.send(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa1"));
        //second request should be send
        server.verifyReceived();

        assertTrue(futResp1.isDone());
        assertFalse(futResp2.isDone());

        //send response for queued inactive transaction
        server.send(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa3").build());

        assertTrue(futResp1.isDone());
        assertEquals("dupa1", futResp1.get().getPayloadString());
        assertTrue(futResp2.isDone());
        assertEquals("dupa3", futResp2.get().getPayloadString());

    }

    @Test
    public void shouldFailToSendTooManyRequests() throws Exception {
        // given
        CompletableFuture<CoapResponse> futResp1 = client.send(get(LOCAL_5683, "/path1"));
        CompletableFuture<CoapResponse> futResp2 = client.send(get(LOCAL_5683, "/path2"));

        // when
        CompletableFuture<CoapResponse> futResp3 = client.send(get(LOCAL_5683, "/path3"));

        // then
        assertTrue(futResp3.isCompletedExceptionally());

        // and first two requests should be handled
        server.send(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("ok1").build());
        server.send(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("ok2").build());
        assertEquals("ok1", futResp1.join().getPayloadString());
        assertEquals("ok2", futResp2.join().getPayloadString());
    }

}
