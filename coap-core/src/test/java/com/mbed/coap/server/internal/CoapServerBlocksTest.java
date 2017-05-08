/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.internal;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java8.util.concurrent.CompletableFuture;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;

/**
 * Created by szymon
 */
public class CoapServerBlocksTest {


    private final CoapTransport coapTransport = mock(CoapTransport.class);
    private int mid = 100;
    private final MessageIdSupplier midSupplier = () -> mid++;
    private CoapServerBlocks server;
    private CoapUdpMessaging protoServer;
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    private CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();


    @Before
    public void setUp() {
        protoServer = new CoapUdpMessaging(coapTransport);
        server = new CoapServerBlocks(protoServer, capabilities, 10000000) {
            @Override
            public byte[] observe(String uri, InetSocketAddress destination, Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) {
                return new byte[0];
            }

            @Override
            public byte[] observe(CoapPacket request, Callback<CoapPacket> respCallback, TransportContext transportContext) {
                return new byte[0];
            }
        };

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void block2_response() throws Exception {
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        server.addRequestHandler("/block", new ReadOnlyCoapResource("123456789012345|abcd"));

        //block 0
        receive(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(1).get().uriPath("/block").block2Res(0, BlockSize.S_16, false));
        assertSent(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|"));

        //block 1
        receive(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(2).get().uriPath("/block").block2Res(1, BlockSize.S_16, false));
        assertSent(newCoapPacket(CoapPacketBuilder.LOCAL_5683).mid(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("abcd"));
    }

    @Test
    public void block1_request() throws Exception {
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        server.addRequestHandler("/block", exchange -> {
            if (exchange.getRequestBodyString().equals("123456789012345|abcd")) {
                exchange.setResponseCode(Code.C204_CHANGED);
                exchange.sendResponse();
            } else {
                exchange.sendResetResponse();
            }
        });

        //block 1
        receive(newCoapPacket(LOCAL_5683).mid(1).put().block1Req(0, BlockSize.S_16, true).size1(20).uriPath("/block").payload("123456789012345|"));
        assertSent(newCoapPacket(LOCAL_5683).mid(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true));

        //block 2
        receive(newCoapPacket(LOCAL_5683).mid(2).put().block1Req(1, BlockSize.S_16, false).uriPath("/block").payload("abcd"));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false));
    }

    @Test
    public void block1_request_shouldFailIfTooBigPayload() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(5000, true));
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server = new CoapServerBlocks(protoServer, capabilities, 10000) {
            @Override
            public byte[] observe(String uri, InetSocketAddress destination, Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) {
                return new byte[0];
            }

            @Override
            public byte[] observe(CoapPacket request, Callback<CoapPacket> respCallback, TransportContext transportContext) {
                return new byte[0];
            }
        };
        server.start();

        server.addRequestHandler("/block", exchange -> {
            fail("Should not receive exchange");
        });


        byte[] payloadBlock1 = generatePayload(0, 4096).toByteArray();
        byte[] payloadBlock2 = generatePayload(4, 4096).toByteArray();
        byte[] payloadBlock3 = generatePayload(8, 4096).toByteArray();

        final ByteArrayOutputStream fullPayload = new ByteArrayOutputStream(payloadBlock1.length + payloadBlock2.length + payloadBlock3.length);
        fullPayload.write(payloadBlock1);
        fullPayload.write(payloadBlock2);
        fullPayload.write(payloadBlock3);

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
    public void block1_incorrectIntermediateBlockSize() throws CoapException, IOException {
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        server.addRequestHandler("/block", exchange -> {
            fail("Should not receive exchange");
        });

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
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1250, true));
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        byte[] payloadBlock1 = generatePayload(0, 4096).toByteArray();
        byte[] payloadBlock2 = generatePayload(4, 2048).toByteArray();
        ByteArrayOutputStream payloadBlockFinalOs = generatePayload(6, 1024);
        payloadBlockFinalOs.write("end_of_payload".getBytes());
        byte[] payloadFinal = payloadBlockFinalOs.toByteArray();

        final ByteArrayOutputStream fullPayload = new ByteArrayOutputStream(payloadBlock1.length + payloadBlock2.length + payloadFinal.length);
        fullPayload.write(payloadBlock1);
        fullPayload.write(payloadBlock2);
        fullPayload.write(payloadFinal);

        server.addRequestHandler("/block", exchange -> {
            if (Arrays.equals(exchange.getRequestBody(), fullPayload.toByteArray())) {
                exchange.setResponseCode(Code.C204_CHANGED);
                exchange.sendResponse();
            } else {
                exchange.sendResetResponse();
            }
        });

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
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1150, true)); // non-BERT
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        byte[] payloadBlock1 = generatePayload(0, 4096).toByteArray();

        server.addRequestHandler("/block", exchange -> {
            fail("Unexpected request was received");
        });

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C402_BAD_OPTION)
                .payload("BERT is not supported"));
    }

    @Test
    public void block1_noBlockEnabled_BERT_received() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1150, false)); // non-BERT
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        byte[] payloadBlock1 = generatePayload(0, 4096).toByteArray();

        server.addRequestHandler("/block", exchange -> {
            fail("Unexpected request was received");
        });

        CoapPacket pkt = newCoapPacket(LOCAL_5683).mid(10).token(0x1234).put().block1Req(0, BlockSize.S_1024_BERT, true).uriPath("/block").build();
        pkt.setPayload(payloadBlock1);
        receive(pkt);
        assertSent(newCoapPacket(LOCAL_5683).mid(10).token(0x1234).ack(Code.C402_BAD_OPTION)
                .payload("BERT is not supported"));
    }

    @Test
    public void block1_BERT_incorrectIntermediateBlockSize() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1250, true));
        protoServer.init(10, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        server.start();

        byte[] payloadBlock1 = generatePayload(0, 4096).toByteArray();

        ByteArrayOutputStream payloadBlock2BrokenOs = generatePayload(4, 2048);
        payloadBlock2BrokenOs.write("wrong pl interm block".getBytes());
        byte[] payloadBlock2Broken = payloadBlock2BrokenOs.toByteArray();

        ByteArrayOutputStream payloadBlockFinalOs = generatePayload(6, 1024);
        payloadBlockFinalOs.write("end_of_payload".getBytes());
        byte[] payloadFinal = payloadBlockFinalOs.toByteArray();

        server.addRequestHandler("/block", exchange -> {
            fail("Unexpected request was received");
        });

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


    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(BlockRequestId.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();

    }


    private void receive(CoapPacketBuilder coapPacketBuilder) {
        protoServer.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void receive(CoapPacket coapPacket) {
        protoServer.handle(coapPacket, TransportContext.NULL);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacketBuilder.build()), any(), any());
    }

    private ByteArrayOutputStream generatePayload(int startBlockNumber, int size16bRound) throws Exception {
        if (size16bRound % 16 != 0) {
            throw new Exception("Size should be 16 bytes blocks");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(size16bRound + 32);

        int blocks = size16bRound / 16;

        for (int i = 0; i < blocks; i++) {
            bos.write(String.format("%03d_456789ABCDE|", i + startBlockNumber).getBytes());
        }
        return bos;
    }
}