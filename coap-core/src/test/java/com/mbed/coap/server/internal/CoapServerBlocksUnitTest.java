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

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.RouterService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerBlocksUnitTest {

    private final CoapMessaging msg = mock(CoapMessaging.class);
    private CompletableFuture<CoapPacket> promise;
    private CoapServerBlocks server;
    private CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();

    @BeforeEach
    public void setUp() throws Exception {
        reset(msg);

        given(msg.makeRequest(any(), any())).willAnswer(__ -> newPromise());

        server = new CoapServerBlocks(msg, capabilities, 100_000, RouterService.NOT_FOUND_SERVICE);
        server.start();
    }

    private CompletableFuture<CoapPacket> newPromise() {
        promise = new CompletableFuture<>();
        return promise;
    }

    @Test
    public void shouldMakeNonBlockingRequest() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(0).get().uriPath("/test").build();

        CompletableFuture<CoapResponse> resp = server.clientService().apply(get(LOCAL_5683, "/test"));

        verify(msg).makeRequest(eq(req), any());
        assertFalse(resp.isDone());
    }

    @Test
    public void shouldReceiveNonBlockingResponse() throws Exception {
        CoapPacket resp = newCoapPacket(LOCAL_5683).mid(2).con(Code.C205_CONTENT).payload("OK").build();

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(get(LOCAL_5683, "/test"));

        assertFalse(respFut.isDone());

        //verify response
        promise.complete(resp);

        assertEquals(CoapResponse.ok("OK"), respFut.get());
    }

    @Test
    public void shouldMakeBlockingRequest_maxMsgSz20() throws Exception {
        CoapRequest req = post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_");
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").size1(32).block1Req(0, BlockSize.S_16, true)
        );

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, false)
        );
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldFail_toReceive_tooLarge_blockingResponse() throws Exception {
        server = new CoapServerBlocks(msg, capabilities, 2000, RouterService.NOT_FOUND_SERVICE);
        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequestAndReceive(
                newCoapPacket(LOCAL_5683).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_1024, true).payload(opaqueOfSize(1024))
        );

        //BLOCK 1
        assertMakeRequestAndReceive(
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).ack(Code.C205_CONTENT).payload(opaqueOfSize(1000))
        );


        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(respFut::get).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void shoudFail_toReceive_responseWithIncorrectLastBlockSize() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0123456789ABCDEF").build());

        //BLOCK 1
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("0123456789abcdef_").build());

        //verify
        assertTrue(respFut.isDone());
        assertThatThrownBy(() -> respFut.get())
                .hasCauseExactlyInstanceOf(CoapBlockException.class)
                .hasMessageStartingWith("com.mbed.coap.exception.CoapBlockException: Last block size mismatch with block option");
    }

    @Test
    public void shouldReceiveBlockingResponse() throws Exception {
        CoapRequest req = get(LOCAL_5683, "/test");
        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("LARGE___PAYLOAD_").build());

        //BLOCK 1
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("LARGE___PAYLOAD_").build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals("LARGE___PAYLOAD_LARGE___PAYLOAD_", respFut.get().getPayloadString());
    }

    @Test
    public void shouldReceiveBlockingResponse_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.1
        CoapRequest req = get(LOCAL_5683, "/status");

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, S_1024_BERT, true).payload(opaqueOfSize(3072)).build());

        //BLOCK 1
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(3, S_1024_BERT, false));

        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(3, S_1024_BERT, true).payload(opaqueOfSize(5120)).build());

        //BLOCK 2
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(8, S_1024_BERT, false));

        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(8, S_1024_BERT, false).payload(opaqueOfSize(4711)).build());


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

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(0, S_1024_BERT, true).payload(opaqueOfSize(8192)).size1(22067));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(0, S_1024_BERT, true).build());

        //BLOCK 1
        assertMakeRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(8, S_1024_BERT, true).payload(opaqueOfSize(8192)));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(8, S_1024_BERT, true).build());

        //BLOCK 2
        assertMakeRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(16, S_1024_BERT, false).payload(opaqueOfSize(5683)));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C204_CHANGED).block1Req(16, S_1024_BERT, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldFailSendBlockingRequest_when_blockTransferIsDisabled() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2

        CoapRequest req = put(LOCAL_5683, "/options").payload(opaqueOfSize(8192 + 8192 + 5683));

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(() -> respFut.get())
                .hasCause(new CoapException("Block transfers are not enabled for localhost/127.0.0.1:5683 and payload size 22067 > max payload size 1152"));

        verify(msg, never()).makeRequest(any(), any());
    }


    @Test
    public void should_continue_block_transfer_after_block_size_change() throws ExecutionException, InterruptedException {
        CoapRequest req = post(LOCAL_5683, "/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_LARGE___PAYLOAD");
        capabilities.put(LOCAL_5683, new CoapTcpCSM(40, true));

        CompletableFuture<CoapResponse> respFut = server.clientService().apply(req);

        //BLOCK 0
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").size1(47).block1Req(0, BlockSize.S_32, true)
        );

        //response new size=16
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, true)
        );
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(1, BlockSize.S_16, false).build());

        //BLOCK 2
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD").block1Req(2, BlockSize.S_16, false)
        );
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    //--------------------------

    private void assertMakeRequest(CoapPacketBuilder req) {
        assertMakeRequest(req.build());
    }

    private void assertMakeRequest(CoapPacket req) {
        verify(msg).makeRequest(eq(req), any());
    }

    private void assertMakeRequestAndReceive(CoapPacketBuilder req, CoapPacketBuilder receive) {
        assertMakeRequest(req);

        //response
        clearInvocations(msg);
        assertTrue(promise.complete(receive.build()));
    }

}