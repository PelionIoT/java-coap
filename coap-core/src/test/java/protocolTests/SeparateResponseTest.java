/**
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
package protocolTests;

import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.MessageIdSupplierImpl;
import com.mbed.coap.transmission.SingleTimeout;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon.
 */
public class SeparateResponseTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;

    @Before
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldResponseWithEmptyAckAndSeparateResponse() throws Exception {
        //empty ack
        transport.when(newCoapPacket(1).token(123).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(null).build());

        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response
        transport.receive(newCoapPacket(2).token(123).non(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponse_withoutEmptyAck() throws Exception {
        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response, no empty ack
        transport.receive(newCoapPacket(2).token(123).con(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);
        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponseBlock1_withoutEmptyAck() throws Exception {
        //given
        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).payload("aaaaaaaaa|aaaaaaaaa|aaaaaaaaa|aaaaaaaaa|").post();

        //when
        transport.receive(newCoapPacket(1).token(123).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_32, true).build(), SERVER_ADDRESS);

        //and, separate response
        transport.receive(newCoapPacket(2).emptyAck(2), SERVER_ADDRESS);
        transport.receive(newCoapPacket(3).token(123).con(Code.C201_CREATED).block1Req(1, BlockSize.S_32, false).payload("ok").build(), SERVER_ADDRESS);

        //then
        assertEquals("ok", futResp.get().getPayloadString());
        //and ACK response should be sent
        assertEquals(newCoapPacket(SERVER_ADDRESS).emptyAck(3), transport.getLastOutgoingMessage());
    }
}
