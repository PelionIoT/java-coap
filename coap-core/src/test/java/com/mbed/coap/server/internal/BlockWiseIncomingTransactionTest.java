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
package com.mbed.coap.server.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapRequestEntityIncomplete;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Code;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

/**
 * Created by szymon
 */
public class BlockWiseIncomingTransactionTest {

    BlockWiseIncomingTransaction bwReq = new BlockWiseIncomingTransaction(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[1024]).put().build(), 10_000, new CoapTcpCSM(4096, true));

    @Test
    public void should_appendBlock() throws Exception {
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_512, true).payload(new byte[512]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(1, BlockSize.S_512, true).payload(new byte[512]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_512, false).payload(new byte[100]).put().build());

        assertEquals(1124, bwReq.getCombinedPayload().length);
    }

    @Test
    public void should_appendBlock_with_different_tokens() throws Exception {
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).token(1).block1Req(0, BlockSize.S_512, true).payload(new byte[512]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).token(2).block1Req(1, BlockSize.S_512, true).payload(new byte[512]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).token(3).block1Req(2, BlockSize.S_512, false).payload(new byte[100]).put().build());

        assertEquals(1124, bwReq.getCombinedPayload().length);
    }

    @Test
    public void should_appendBlock_changing_block_sizes() throws Exception {
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_512, true).payload(new byte[512]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_256, true).payload(new byte[256]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(6, BlockSize.S_128, false).payload(new byte[100]).put().build());

        assertEquals(868, bwReq.getCombinedPayload().length);
    }

    @Test
    public void should_appendBlock_restart_from_beginning() throws Exception {
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_256, true).payload(new byte[256]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_128, true).payload(new byte[128]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(1, BlockSize.S_128, true).payload(new byte[128]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_128, false).payload(new byte[100]).put().build());

        assertEquals(356, bwReq.getCombinedPayload().length);
    }

    @Test
    public void should_appendBlock_bert() throws Exception {

        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[2048]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_1024_BERT, true).payload(new byte[2048]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(4, BlockSize.S_1024_BERT, false).payload(new byte[100]).put().build());

        assertEquals(4196, bwReq.getCombinedPayload().length);
    }

    @Test
    public void should_fail_to_appendBlock_when_too_large_total_payload() throws Exception {

        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[4096]).put().build());
        bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(4, BlockSize.S_1024_BERT, true).payload(new byte[4096]).put().build());

        assertThatThrownBy(() ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(8, BlockSize.S_1024_BERT, false).payload(new byte[2000]).put().build())
        ).isExactlyInstanceOf(CoapRequestEntityTooLarge.class);
    }

    @Test
    public void should_fail_with_invalid_request() throws Exception {
        //missing previous blocks
        assertThatThrownBy(() ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_512, false).payload(new byte[512]).get().build())
        ).isExactlyInstanceOf(CoapRequestEntityIncomplete.class);


        //size too large for defined capabilities
        assertThatThrownBy(() ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).size1(11_000).block1Req(0, BlockSize.S_512, true).payload(new byte[512]).get().build())
        ).isExactlyInstanceOf(CoapRequestEntityTooLarge.class);

        //payload size does not match block size
        assertCodeException(Code.C413_REQUEST_ENTITY_TOO_LARGE, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_512, true).payload(new byte[100]).get().build())
        );

        //no payload
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_512, true).get().build())
        );

        //last block and payload size larger that block size
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(2, BlockSize.S_512, false).payload(new byte[600]).get().build())
        );

    }

    @Test
    public void should_fail_with_invalid_request_bert() throws Exception {
        //-- FAILURES --

        //no blocks allowed
        bwReq = new BlockWiseIncomingTransaction(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[1024]).put().build(), 10_000,
                new CoapTcpCSM(4096, false));
        assertCodeException(Code.C402_BAD_OPTION, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[2048]).get().build())
        );

        //blocks but not bert
        bwReq = new BlockWiseIncomingTransaction(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[1024]).put().build(), 10_000,
                new CoapTcpCSM(512, true));
        assertCodeException(Code.C402_BAD_OPTION, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[2048]).get().build())
        );

        //payload size does not match block size
        bwReq = new BlockWiseIncomingTransaction(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[1024]).put().build(), 10_000,
                new CoapTcpCSM(10_000, true));
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[100]).get().build())
        );

        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).payload(new byte[2000]).get().build())
        );

        //missing payload
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(newCoapPacket(LOCAL_5683).block1Req(0, BlockSize.S_1024_BERT, true).get().build())
        );
    }


    private static void assertCodeException(Code expectedCode, ThrowableAssert.ThrowingCallable call) {
        CoapCodeException ex = (CoapCodeException) catchThrowable(call);
        assertEquals(expectedCode, ex.getCode());
    }
}