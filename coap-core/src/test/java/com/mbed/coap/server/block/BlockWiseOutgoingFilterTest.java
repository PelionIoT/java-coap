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
package com.mbed.coap.server.block;

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.of;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.messaging.CoapTcpCSM;
import com.mbed.coap.server.messaging.CoapTcpCSMStorageImpl;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class BlockWiseOutgoingFilterTest {

    private final CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();
    private CompletableFuture<CoapResponse> promise;
    private CoapRequest lastReq;
    private BlockWiseOutgoingFilter filter = new BlockWiseOutgoingFilter(capabilities, 100_000);
    private Service<CoapRequest, CoapResponse> service = filter.then(this::newPromise);

    private CompletableFuture<CoapResponse> newPromise(CoapRequest req) {
        promise = new CompletableFuture<>();
        lastReq = req;
        return promise;
    }

    @Test
    public void shouldMakeNonBlockingRequest() throws Exception {
        CoapRequest req = get(LOCAL_5683, "/test");

        CompletableFuture<CoapResponse> resp = service.apply(get(LOCAL_5683, "/test"));

        assertEquals(req, lastReq);
        assertFalse(resp.isDone());
    }

    @Test
    public void shouldReceiveNonBlockingResponse() throws Exception {
        CompletableFuture<CoapResponse> respFut = service.apply(get(LOCAL_5683, "/test"));

        assertFalse(respFut.isDone());

        //verify response
        promise.complete(ok(Opaque.of("OK")));

        assertEquals(ok("OK"), respFut.get());
    }


    @Test
    public void shouldMakeBlockingRequest_maxMsgSz20() throws Exception {
        CoapRequest req = post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_");
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(
                post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_").size1(32).block1Req(0, BlockSize.S_16, true)
        );

        //response
        promise.complete(of(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false));


        //BLOCK 1
        assertMakeRequest(
                post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, false)
        );
        promise.complete(of(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false));

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }


    @Test
    public void shoudFail_toReceive_responseWithIncorrectLastBlockSize() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(get(LOCAL_5683, "/test"));

        //response
        promise.complete(of(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload(Opaque.of("0123456789ABCDEF")));

        //BLOCK 1
        assertMakeRequest(get(LOCAL_5683, "/test").block2Res(1, BlockSize.S_16, false));

        promise.complete(of(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload(Opaque.of("0123456789abcdef_")));

        //verify
        assertTrue(respFut.isDone());
        assertThatThrownBy(() -> respFut.get())
                .hasCauseExactlyInstanceOf(CoapBlockException.class)
                .hasMessageStartingWith("com.mbed.coap.exception.CoapBlockException: Last block size mismatch with block option");
    }

    @Test
    public void shouldFail_toReceive_tooLarge_blockingResponse() throws Exception {
        service = new BlockWiseOutgoingFilter(capabilities, 2000).then(this::newPromise);
        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequestAndReceive(
                get(LOCAL_5683, "/test"),
                of(Code.C205_CONTENT).block2Res(0, BlockSize.S_1024, true).payload(opaqueOfSize(1024))
        );

        //BLOCK 1
        assertMakeRequestAndReceive(
                get(LOCAL_5683, "/test").block2Res(1, BlockSize.S_1024, false),
                ok(opaqueOfSize(1000)).block2Res(1, BlockSize.S_1024, false)
        );


        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(respFut::get).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void shouldReceiveBlockingResponse() throws Exception {
        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(get(LOCAL_5683, "/test"));

        //response
        promise.complete(of(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload(Opaque.of("LARGE___PAYLOAD_")));

        //BLOCK 1
        assertMakeRequest(get(LOCAL_5683, "/test").block2Res(1, BlockSize.S_16, false));

        promise.complete(of(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload(Opaque.of("LARGE___PAYLOAD_")));

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals("LARGE___PAYLOAD_LARGE___PAYLOAD_", respFut.get().getPayloadString());
    }

    @Test
    public void shouldReceiveBlockingResponse_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.1
        CoapRequest req = get(LOCAL_5683, "/status");

        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(get(LOCAL_5683, "/status"));

        //response
        promise.complete(of(Code.C205_CONTENT).block2Res(0, S_1024_BERT, true).payload(opaqueOfSize(3072)));

        //BLOCK 1
        assertMakeRequest(get(LOCAL_5683, "/status").block2Res(3, S_1024_BERT, false));

        promise.complete(of(Code.C205_CONTENT).block2Res(3, S_1024_BERT, true).payload(opaqueOfSize(5120)));

        //BLOCK 2
        assertMakeRequest(get(LOCAL_5683, "/status").block2Res(8, S_1024_BERT, false));

        promise.complete(of(Code.C205_CONTENT).block2Res(8, S_1024_BERT, false).payload(opaqueOfSize(4711)));


        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals(3072 + 5120 + 4711, respFut.get().getPayload().size());
    }

    @Test
    public void shouldSendBlockingRequest_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2
        capabilities.put(LOCAL_5683, new CoapTcpCSM(10000, true));

        CoapRequest req = put(LOCAL_5683, "/options").payload(opaqueOfSize(8192 + 8192 + 5683));

        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(put(LOCAL_5683, "/options").block1Req(0, S_1024_BERT, true).payload(opaqueOfSize(8192)).size1(22067));

        promise.complete(of(Code.C231_CONTINUE).block1Req(0, S_1024_BERT, true));

        //BLOCK 1
        assertMakeRequest(put(LOCAL_5683, "/options").block1Req(8, S_1024_BERT, true).payload(opaqueOfSize(8192)));

        promise.complete(of(Code.C231_CONTINUE).block1Req(8, S_1024_BERT, true));

        //BLOCK 2
        assertMakeRequest(put(LOCAL_5683, "/options").block1Req(16, S_1024_BERT, false).payload(opaqueOfSize(5683)));

        promise.complete(of(Code.C204_CHANGED).block1Req(16, S_1024_BERT, false));

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }


    @Test
    public void shouldFailSendBlockingRequest_when_blockTransferIsDisabled() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2

        CoapRequest req = put(LOCAL_5683, "/options").payload(opaqueOfSize(8192 + 8192 + 5683));

        CompletableFuture<CoapResponse> respFut = service.apply(req);

        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(() -> respFut.get())
                .hasCause(new CoapException("Block transfers are not enabled for localhost/127.0.0.1:5683 and payload size 22067 > max payload size 1152"));

        assertNull(lastReq);
    }


    @Test
    public void should_continue_block_transfer_after_block_size_change() throws ExecutionException, InterruptedException {
        CoapRequest req = post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_LARGE___PAYLOAD");
        capabilities.put(LOCAL_5683, new CoapTcpCSM(40, true));

        CompletableFuture<CoapResponse> respFut = service.apply(req);

        //BLOCK 0
        assertMakeRequest(
                post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").size1(47).block1Req(0, BlockSize.S_32, true)
        );

        //response new size=16
        promise.complete(of(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false));


        //BLOCK 1
        assertMakeRequest(
                post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, true)
        );
        promise.complete(of(Code.C231_CONTINUE).block1Req(1, BlockSize.S_16, false));

        //BLOCK 2
        assertMakeRequest(
                post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD").block1Req(2, BlockSize.S_16, false)
        );
        promise.complete(of(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false));

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    private void assertMakeRequestAndReceive(CoapRequest req, CoapResponse resp) {
        assertMakeRequest(req);

        //response
        assertTrue(promise.complete(resp));
    }

    private void assertMakeRequest(CoapRequest req) {
        assertEquals(req, lastReq);
        lastReq = null;
    }


}