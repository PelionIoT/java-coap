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

import static com.mbed.coap.packet.BlockSize.S_32;
import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapRequest.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import com.mbed.coap.transmission.SingleTimeout;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.MockCoapTransport;

public class SeparateResponseTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private CoapClient client;
    private MockCoapTransport.MockClient server;

    @BeforeEach
    public void setUp() throws Exception {
        MockCoapTransport serverTransport = new MockCoapTransport();
        server = serverTransport.client();

        client = CoapServer.builder().transport(serverTransport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(S_32)
                .retransmission(new SingleTimeout(500))
                .buildClient(SERVER_ADDRESS);
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldResponseWithEmptyAckAndSeparateResponse() throws Exception {
        CompletableFuture<CoapResponse> futResp = client.send(get("/path1").token(123));
        server.verifyReceived(newCoapPacket(SERVER_ADDRESS).mid(1).token(123).get().uriPath("/path1"));

        // when, send empty ack
        server.send(newCoapPacket(SERVER_ADDRESS).emptyAck(1));

        // and, separate response
        server.send(newCoapPacket(SERVER_ADDRESS).mid(321).token(123).non(Code.C205_CONTENT).payload("dupa"));

        // then
        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponse_withoutEmptyAck() throws Exception {
        CompletableFuture<CoapResponse> futResp = client.send(get("/path1").token(123));

        //separate response, no empty ack
        server.send(newCoapPacket(SERVER_ADDRESS).mid(917).token(123).con(Code.C205_CONTENT).payload("dupa"));

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponseBlock1_withoutEmptyAck() throws Exception {
        //given
        CompletableFuture<CoapResponse> futResp = client.send(post("/path1").token(123).payload("aaaaaaaaa|aaaaaaaaa|aaaaaaaaa|aaaaaaaaa|"));
        server.verifyReceived(newCoapPacket(SERVER_ADDRESS).mid(1).token(123).post().uriPath("/path1").block1Req(0, S_32, true).size1(40).payload("aaaaaaaaa|aaaaaaaaa|aaaaaaaaa|aa"));

        //when
        server.send(newCoapPacket(SERVER_ADDRESS).mid(1).token(123).ack(Code.C231_CONTINUE).block1Req(0, S_32, true));
        server.verifyReceived(newCoapPacket(SERVER_ADDRESS).mid(2).token(123).post().uriPath("/path1").block1Req(1, S_32, false).payload("aaaaaaa|"));

        //and, separate response
        server.send(newCoapPacket(SERVER_ADDRESS).emptyAck(2));
        server.send(newCoapPacket(SERVER_ADDRESS).mid(3).token(123).con(Code.C201_CREATED).block1Req(1, S_32, false).payload("ok"));

        //then
        assertEquals("ok", futResp.get().getPayloadString());
        //and ACK response should be sent
        server.verifyReceived(newCoapPacket(SERVER_ADDRESS).emptyAck(3));
    }
}
