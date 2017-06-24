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
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
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
    private CoapServer server;
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    private BlockSize blockSize = null;


    @Before
    public void setUp() throws Exception {
        server = new CoapServerBlocks() {
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
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

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
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

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
    public void block1_request_failAfterTokenMismatch() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, CoapTransaction.Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        server.addRequestHandler("/block", CoapExchange::sendResponse);

        //block 1
        receive(newCoapPacket(LOCAL_5683).mid(1).token(300).put().block1Req(0, BlockSize.S_16, true).size1(20).uriPath("/block").payload("123456789012345|"));
        assertSent(newCoapPacket(LOCAL_5683).mid(1).token(300).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true));

        //block 2
        receive(newCoapPacket(LOCAL_5683).mid(2).token(999).put().block1Req(1, BlockSize.S_16, false).uriPath("/block").payload("abcd"));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).token(999).ack(Code.C408_REQUEST_ENTITY_INCOMPLETE).block1Req(1, BlockSize.S_16, false).payload("Token mismatch"));
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(CoapServerBlocks.BlockRequestId.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();

    }



    private void receive(CoapPacketBuilder coapPacketBuilder) {
        server.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void receive(CoapPacket coapPacket) {
        server.handle(coapPacket, TransportContext.NULL);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacketBuilder.build()), any(), any());
    }
}