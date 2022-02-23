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
package com.mbed.coap.server.internal;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapRequestEntityIncomplete;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

/**
 * Created by szymon
 */
public class BlockWiseIncomingTransactionTest {

    BlockWiseIncomingTransaction bwReq = new BlockWiseIncomingTransaction(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)), 10_000, new CoapTcpCSM(4096, true));

    @Test
    public void should_appendBlock() throws Exception {
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(opaqueOfSize(512)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(opaqueOfSize(512)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, false).payload(opaqueOfSize(100)));

        assertEquals(1124, bwReq.getCombinedPayload().size());
    }

    @Test
    public void should_appendBlock_with_different_tokens() throws Exception {
        bwReq.appendBlock(put(LOCAL_5683, "/").token(1).block1Req(0, BlockSize.S_512, true).payload(opaqueOfSize(512)));
        bwReq.appendBlock(put(LOCAL_5683, "/").token(2).block1Req(1, BlockSize.S_512, true).payload(opaqueOfSize(512)));
        bwReq.appendBlock(put(LOCAL_5683, "/").token(3).block1Req(2, BlockSize.S_512, false).payload(opaqueOfSize(100)));

        assertEquals(1124, bwReq.getCombinedPayload().size());
    }

    @Test
    public void should_appendBlock_changing_block_sizes() throws Exception {
        Opaque payload0 = opaqueOfSize(0, 512);
        Opaque payload2 = opaqueOfSize(2, 256);
        Opaque payload6 = opaqueOfSize(6, 100);
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_256, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(6, BlockSize.S_128, false).payload(payload6));

        assertEquals(payload0.concat(payload2).concat(payload6), bwReq.getCombinedPayload());
    }

    @Test
    public void should_appendBlock_with_resend_variable_sizes() throws Exception {
        Opaque payload0 = opaqueOfSize(0, 512);
        Opaque payload2 = opaqueOfSize(2, 256);
        Opaque payload6 = opaqueOfSize(6, 100);
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_256, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_256, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(6, BlockSize.S_128, false).payload(payload6));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(6, BlockSize.S_128, false).payload(payload6));
        assertEquals(payload0.concat(payload2).concat(payload6), bwReq.getCombinedPayload());
    }

    @Test
    public void should_appendBlock_with_resend() throws Exception {
        Opaque payload0 = opaqueOfSize(0, 512);
        Opaque payload1 = opaqueOfSize(1, 512);
        Opaque payload2 = opaqueOfSize(2, 512);
        Opaque payload3 = opaqueOfSize(3, 92);
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));

        assertEquals(payload0.concat(payload1).concat(payload2).concat(payload3), bwReq.getCombinedPayload());
    }

    @Test
    public void should_appendBlock_with_resend1() throws Exception {
        Opaque payload0 = opaqueOfSize(0, 512);
        Opaque payload1 = opaqueOfSize(1, 512);
        Opaque payload2 = opaqueOfSize(2, 512);
        Opaque payload3 = opaqueOfSize(3, 92);
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));

        assertEquals(payload0.concat(payload1).concat(payload2).concat(payload3), bwReq.getCombinedPayload());
    }

    @Test
    public void should_appendBlock_with_resend2() throws Exception {
        Opaque payload0 = opaqueOfSize(0, 512);
        Opaque payload1 = opaqueOfSize(1, 512);
        Opaque payload2 = opaqueOfSize(2, 512);
        Opaque payload3 = opaqueOfSize(3, 92);
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(payload0));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, true).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_512, true).payload(payload1));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, false).payload(payload2));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(3, BlockSize.S_512, false).payload(payload3));

        assertEquals(payload0.concat(payload1).concat(payload2).concat(payload3), bwReq.getCombinedPayload());
    }

    @Test
    public void should_appendBlock_restart_from_beginning() throws Exception {
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_256, true).payload(opaqueOfSize(256)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_128, true).payload(opaqueOfSize(128)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(1, BlockSize.S_128, true).payload(opaqueOfSize(128)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_128, false).payload(opaqueOfSize(100)));

        assertEquals(356, bwReq.getCombinedPayload().size());
    }

    @Test
    public void should_appendBlock_bert() throws Exception {

        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(2048)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(2, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(2048)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(4, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(100)));

        assertEquals(4196, bwReq.getCombinedPayload().size());
    }

    @Test
    public void should_fail_to_appendBlock_when_too_large_total_payload() throws Exception {

        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)));
        bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(4, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)));

        assertThatThrownBy(() ->
                bwReq.appendBlock(put(LOCAL_5683, "/").block1Req(8, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(2000)))
        ).isExactlyInstanceOf(CoapRequestEntityTooLarge.class);
    }

    @Test
    public void should_fail_with_invalid_request() throws Exception {
        //missing previous blocks
        assertThatThrownBy(() ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, false).payload(opaqueOfSize(512)))
        ).isExactlyInstanceOf(CoapRequestEntityIncomplete.class);


        //size too large for defined capabilities
        assertThatThrownBy(() ->
                bwReq.appendBlock(get(LOCAL_5683, "/").size1(11_000).block1Req(0, BlockSize.S_512, true).payload(opaqueOfSize(512)))
        ).isExactlyInstanceOf(CoapRequestEntityTooLarge.class);

        //payload size does not match block size
        assertCodeException(Code.C413_REQUEST_ENTITY_TOO_LARGE, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true).payload(opaqueOfSize(100)))
        );

        //no payload
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_512, true))
        );

        //last block and payload size larger that block size
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(2, BlockSize.S_512, false).payload(opaqueOfSize(600)))
        );

    }

    @Test
    public void should_fail_with_invalid_request_bert() throws Exception {
        //-- FAILURES --

        //no blocks allowed
        bwReq = new BlockWiseIncomingTransaction(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)), 10_000,
                new CoapTcpCSM(4096, false));
        assertCodeException(Code.C402_BAD_OPTION, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(2048)))
        );

        //blocks but not bert
        bwReq = new BlockWiseIncomingTransaction(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)), 10_000,
                new CoapTcpCSM(512, true));
        assertCodeException(Code.C402_BAD_OPTION, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(2048)))
        );

        //payload size does not match block size
        bwReq = new BlockWiseIncomingTransaction(put(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)), 10_000,
                new CoapTcpCSM(10_000, true));
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(100)))
        );

        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(2000)))
        );

        //missing payload
        assertCodeException(Code.C400_BAD_REQUEST, () ->
                bwReq.appendBlock(get(LOCAL_5683, "/").block1Req(0, BlockSize.S_1024_BERT, true))
        );
    }

    private static void assertCodeException(Code expectedCode, ThrowableAssert.ThrowingCallable call) {
        CoapCodeException ex = (CoapCodeException) catchThrowable(call);
        assertEquals(expectedCode, ex.getCode());
    }
}
