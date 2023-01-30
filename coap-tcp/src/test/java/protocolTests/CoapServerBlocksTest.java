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

import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.fail;
import static protocolTests.utils.CoapPacketBuilder.LOCAL_5683;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.server.TcpCoapServer;
import com.mbed.coap.server.messaging.Capabilities;
import com.mbed.coap.server.messaging.CapabilitiesStorageImpl;
import com.mbed.coap.utils.Service;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;
import protocolTests.utils.MockCoapTcpTransport;
import protocolTests.utils.MockCoapTransport;

public class CoapServerBlocksTest {

    private CoapServer server;
    private MockCoapTransport.MockClient client;
    private CapabilitiesStorageImpl capabilities = new CapabilitiesStorageImpl();

    private Service<CoapRequest, CoapResponse> blockResource = null;

    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .get("/block", req -> blockResource.apply(req))
            .put("/block", req -> blockResource.apply(req))
            .build();

    private final Service<CoapRequest, CoapResponse> alwaysFailService = request -> {
        fail("Should not receive exchange");
        return null;
    };

    @BeforeEach
    public void setUp() {
        MockCoapTcpTransport transport = new MockCoapTcpTransport();

        server = TcpCoapServer.builder().transport(transport).maxIncomingBlockTransferSize(10000000).route(route).csmStorage(capabilities).build();

        client = transport.client();
    }

    @Test
    public void block2_response() throws Exception {
        server.start();

        blockResource = newResource("123456789012345|abcd");

        //block 0
        receive(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(1).get().uriPath("/block").block2Res(0, BlockSize.S_16, false));
        assertSent(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|"));

        //block 1
        receive(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(2).get().uriPath("/block").block2Res(1, BlockSize.S_16, false));
        assertSent(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("abcd"));
    }

    @Test
    public void block1_request() throws Exception {
        server.start();

        blockResource = request -> {
            if (request.getPayload().equals(of("123456789012345|abcd"))) {
                return completedFuture(CoapResponse.of(Code.C204_CHANGED));
            } else {
                return failedFuture(new CoapException(""));
            }
        };

        //block 1
        receive(newCoapPacket(LOCAL_5683).mid(1).put().block1Req(0, BlockSize.S_16, true).size1(20).uriPath("/block").payload("123456789012345|"));
        assertSent(newCoapPacket(LOCAL_5683).mid(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true));

        //block 2
        receive(newCoapPacket(LOCAL_5683).mid(2).put().block1Req(1, BlockSize.S_16, false).uriPath("/block").payload("abcd"));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false));
    }

    @Test
    public void block1_request_shouldFailIfTooBigPayload() throws Exception {
        capabilities.put(LOCAL_5683, new Capabilities(5000, true));

        MockCoapTcpTransport transport = new MockCoapTcpTransport();
        server = TcpCoapServer.builder().transport(transport).maxIncomingBlockTransferSize(10000).route(route).csmStorage(capabilities).build();
        client = transport.client();

        server.start();

        blockResource = alwaysFailService;


        Opaque payloadBlock1 = generatePayload(0, 4096);
        Opaque payloadBlock2 = generatePayload(4, 4096);
        Opaque payloadBlock3 = generatePayload(8, 4096);

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_1024_BERT, true));

        pkt = newCoapPacket(LOCAL_5683).mid(11).token(0x1234).put().block1Req(4, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock2);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(11).token(0x1234).ack(Code.C231_CONTINUE).block1Req(4, BlockSize.S_1024_BERT, true));

        pkt = newCoapPacket(LOCAL_5683).mid(12).token(0x1234).put().block1Req(8, BlockSize.S_1024_BERT, false).uriPath("/block").build();
        pkt.setPayload(payloadBlock3);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(12).token(0x1234)
                .ack(Code.C413_REQUEST_ENTITY_TOO_LARGE)
                .size1(10000)
                .payload(""));

    }

    @Test
    public void block1_incorrectIntermediateBlockSize() throws Exception {
        server.start();

        blockResource = alwaysFailService;

        //block 1
        receive(newCoapPacket(LOCAL_5683).mid(1).put().block1Req(0, BlockSize.S_16, true).size1(20).uriPath("/block").payload("123456789012345|"));
        assertSent(newCoapPacket(LOCAL_5683).mid(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true));

        //block 2 - broken
        receive(newCoapPacket(LOCAL_5683).mid(2).put().block1Req(1, BlockSize.S_16, true).uriPath("/block").payload("abcd_dsfsd fs fsd fsd fsd fsd fsd fsd fs fsd fsd"));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(Code.C400_BAD_REQUEST)
                .payload("block size mismatch"));

        //block 3 - should fail because no such transaction
        receive(newCoapPacket(LOCAL_5683).mid(3).put().block1Req(2, BlockSize.S_16, false).uriPath("/block").payload("abcd"));
        assertSent(newCoapPacket(LOCAL_5683).mid(3).ack(Code.C408_REQUEST_ENTITY_INCOMPLETE)
                .payload("no prev blocks"));
    }

    @Test
    public void block1_request_BERT_multiblock() throws Exception {
        capabilities.put(LOCAL_5683, new Capabilities(1250, true));
        server.start();

        Opaque payloadBlock1 = generatePayload(0, 4096);
        Opaque payloadBlock2 = generatePayload(4, 2048);
        Opaque payloadBlockFinalOs = generatePayload(6, 1024);
        Opaque payloadFinal = payloadBlockFinalOs.concat(Opaque.of("end_of_payload"));

        final Opaque fullPayload = payloadBlock1.concat(payloadBlock2).concat(payloadFinal);

        blockResource = request -> {
            if (Objects.equals(request.getPayload(), fullPayload)) {
                return completedFuture(CoapResponse.of(Code.C204_CHANGED));
            } else {
                return alwaysFailService.apply(request);
            }
        };

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_1024_BERT, true));

        pkt = newCoapPacket(LOCAL_5683).mid(11).token(0x1235).put().block1Req(4, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock2);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(11).token(0x1235).ack(Code.C231_CONTINUE).block1Req(4, BlockSize.S_1024_BERT, true));

        pkt = newCoapPacket(LOCAL_5683).mid(12).token(0x1236).put().block1Req(6, BlockSize.S_1024_BERT, false).uriPath("/block").build();
        pkt.setPayload(payloadFinal);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(12).token(0x1236).ack(Code.C204_CHANGED).block1Req(6, BlockSize.S_1024_BERT, false));
    }

    @Test
    public void block1_nonBertAgreed_BERT_received() throws Exception {
        capabilities.put(LOCAL_5683, new Capabilities(1150, true)); // non-BERT
        server.start();

        Opaque payloadBlock1 = generatePayload(0, 4096);

        blockResource = alwaysFailService;

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C402_BAD_OPTION)
                .payload("BERT is not supported"));
    }

    @Test
    public void block1_noBlockEnabled_BERT_received() throws Exception {
        capabilities.put(LOCAL_5683, new Capabilities(1150, false)); // non-BERT
        server.start();

        Opaque payloadBlock1 = generatePayload(0, 4096);

        blockResource = alwaysFailService;

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C402_BAD_OPTION)
                .payload("BERT is not supported"));
    }

    @Test
    public void block1_BERT_incorrectIntermediateBlockSize() throws Exception {
        capabilities.put(LOCAL_5683, new Capabilities(1250, true));
        server.start();

        Opaque payloadBlock1 = generatePayload(0, 4096);

        Opaque payloadBlock2BrokenOs = generatePayload(4, 2048);
        Opaque payloadBlock2Broken = payloadBlock2BrokenOs.concat(Opaque.of("wrong pl interm block"));

        Opaque payloadBlockFinalOs = generatePayload(6, 1024);
        Opaque payloadFinal = payloadBlockFinalOs.concat(Opaque.of("end_of_payload"));

        blockResource = alwaysFailService;

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_1024_BERT, true));

        pkt = newCoapPacket(LOCAL_5683).mid(11).token(0x1234).put().block1Req(4, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock2Broken);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(11).token(0x1234).ack(Code.C400_BAD_REQUEST)
                .payload("block size mismatch"));

        // fail unknown block, we don't have such transaction (just removed because of error)

        pkt = newCoapPacket(LOCAL_5683).mid(12).token(0x1234).put().block1Req(6, BlockSize.S_1024_BERT, false).uriPath("/block").build();
        pkt.setPayload(payloadFinal);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(12).token(0x1234).ack(Code.C408_REQUEST_ENTITY_INCOMPLETE)
                .payload("no prev blocks"));
    }


    private void receive(CoapPacketBuilder coapPacketBuilder) {
        client.send(coapPacketBuilder);
    }

    private void receive(CoapPacket coapPacket) {
        client.send(coapPacket);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws InterruptedException {
        client.verifyReceived(coapPacketBuilder);
    }

    private Opaque generatePayload(int startBlockNumber, int size16bRound) throws Exception {
        if (size16bRound % 16 != 0) {
            throw new Exception("Size should be 16 bytes blocks");
        }
        int blocks = size16bRound / 16;

        Opaque newPayload = Opaque.EMPTY;
        for (int i = 0; i < blocks; i++) {
            newPayload = newPayload.concat(Opaque.of(String.format("%03d_456789ABCDE|", i + startBlockNumber)));
        }
        return newPayload;
    }

    private Service<CoapRequest, CoapResponse> newResource(final String payload) {
        return req -> completedFuture(CoapResponse.ok(payload));
    }

}
