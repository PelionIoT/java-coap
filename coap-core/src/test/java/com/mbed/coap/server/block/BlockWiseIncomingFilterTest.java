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
package com.mbed.coap.server.block;

import static com.mbed.coap.packet.BlockSize.S_16;
import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapRequest.put;
import static com.mbed.coap.packet.CoapResponse.of;
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.packet.Code.C204_CHANGED;
import static com.mbed.coap.packet.Code.C205_CONTENT;
import static com.mbed.coap.packet.Code.C231_CONTINUE;
import static com.mbed.coap.utils.Bytes.opaqueOfSize;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.messaging.Capabilities;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BlockWiseIncomingFilterTest {
    private static final InetSocketAddress LOCAL_5683 = new InetSocketAddress("localhost", 5683);
    private Capabilities capability = Capabilities.BASE;
    private final BlockWiseIncomingFilter blockingFilter = new BlockWiseIncomingFilter(__ -> capability, 10000000);
    private CoapRequest lastRequest = null;
    private Service<CoapRequest, CoapResponse> service;

    @Test
    void shouldForwardWhenNonBlockRequestAndResponse() {
        Service<CoapRequest, CoapResponse> service = blockingFilter
                .then(__ -> completedFuture(ok("OK")));

        CompletableFuture<CoapResponse> resp = service.apply(get(LOCAL_5683, "/"));

        assertEquals(ok("OK"), resp.join());
    }

    @Test
    public void should_send_blocks() {
        service = blockingFilter
                .then(req -> {
                    lastRequest = req;
                    return completedFuture(of(C204_CHANGED, "ok"));
                });

        CompletableFuture<CoapResponse> resp;

        //BLOCK 1
        resp = service.apply(
                put(LOCAL_5683, "/").token(1001).block1Req(0, S_16, true).payload(opaqueOfSize(16))
        );
        assertEquals(of(C231_CONTINUE).block1Req(0, S_16, true), resp.join());

        //BLOCK 2
        resp = service.apply(
                put(LOCAL_5683, "/").token(2002).block1Req(1, S_16, true).payload(opaqueOfSize(16))
        );
        assertEquals(of(C231_CONTINUE).block1Req(1, S_16, true), resp.join());

        //BLOCK 3
        resp = service.apply(
                put(LOCAL_5683, "/").token(3003).block1Req(2, S_16, false).payload(opaqueOfSize(1))
        );
        assertEquals(of(C204_CHANGED).block1Req(2, S_16, false).payload("ok"), resp.join());


        assertEquals(put(LOCAL_5683, "/").token(3003).block1Req(2, S_16, false).payload(opaqueOfSize(33)), lastRequest);
    }

    @Test
    public void should_send_error_when_wrong_second_payload_and_block_size() {
        service = blockingFilter
                .then(__ -> completedFuture(ok("OK")));

        //BLOCK 1
        service.apply(put(LOCAL_5683, "/").block1Req(0, S_16, true).payload(opaqueOfSize(16)));
        //BLOCK 2
        CompletableFuture<CoapResponse> resp = service.apply(put(LOCAL_5683, "/").block1Req(1, S_16, true).payload(opaqueOfSize(17)));

        // then
        assertThatThrownBy(resp::join).hasCause(new CoapCodeException(Code.C400_BAD_REQUEST, "block size mismatch"));
    }

    @Test
    public void should_send_error_when_wrong_first_payload_and_block_size() {
        service = blockingFilter
                .then(__ -> completedFuture(ok("OK")));

        //BLOCK 1
        CompletableFuture<CoapResponse> resp = service.apply(put(LOCAL_5683, "/").block1Req(0, S_16, true).payload(opaqueOfSize(17)));

        // then
        assertThatThrownBy(resp::join).hasCause(new CoapCodeException(Code.C400_BAD_REQUEST, "block size mismatch"));
    }

    @Test
    public void shouldSendBlockingResponse_2k_with_BERT() {
        service = blockingFilter
                .then(__ -> completedFuture(ok(opaqueOfSize(2000))));
        capability = new Capabilities(1200, true);
        CompletableFuture<CoapResponse> resp;

        //BLOCK 0
        resp = service.apply(get(LOCAL_5683, "/large"));
        assertEquals(of(C205_CONTENT).block2Res(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)), resp.join());

        //BLOCK 1
        resp = service.apply(get(LOCAL_5683, "/large").block2Res(1, BlockSize.S_1024_BERT, false));
        assertEquals(of(C205_CONTENT).block2Res(1, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(976)), resp.join());
    }

    @Test
    public void shouldSendBlockingResponse_2k_no_BERT_needed() {
        service = blockingFilter
                .then(__ -> completedFuture(ok(opaqueOfSize(2000))));
        capability = new Capabilities(4000, true);

        //when
        CompletableFuture<CoapResponse> resp = service.apply(get(LOCAL_5683, "/large"));

        //then full payload, no blocks
        assertEquals(of(C205_CONTENT).payload(opaqueOfSize(2000)), resp.join());
    }

    @Test
    public void shouldSendBlockingResponse_10k_with_BERT() {
        service = blockingFilter
                .then(__ -> completedFuture(ok(opaqueOfSize(10000))));
        capability = new Capabilities(6000, true);
        CompletableFuture<CoapResponse> resp;

        //BLOCK 0
        resp = service.apply(get(LOCAL_5683, "/xlarge"));
        assertEquals(of(C205_CONTENT).block2Res(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)), resp.join());

        //BLOCK 1
        resp = service.apply(get(LOCAL_5683, "/xlarge").block2Res(4, BlockSize.S_1024_BERT, false));
        assertEquals(of(C205_CONTENT).block2Res(4, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)), resp.join());

        //BLOCK 2
        resp = service.apply(get(LOCAL_5683, "/xlarge").block2Res(8, BlockSize.S_1024_BERT, false));
        assertEquals(of(C205_CONTENT).block2Res(8, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(1808)), resp.join());
    }

}
