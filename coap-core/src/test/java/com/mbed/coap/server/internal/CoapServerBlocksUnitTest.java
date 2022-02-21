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
import static com.mbed.coap.utils.Bytes.*;
import static java.util.concurrent.CompletableFuture.*;
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
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.TransportContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerBlocksUnitTest {

    private final CoapMessaging msg = mock(CoapMessaging.class);
    private CompletableFuture<CoapPacket> promise;
    private CoapServerBlocks server;
    private CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();
    private CoapRequestHandler requestHandler;
    private final AtomicReference<Opaque> receivedPayload = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws Exception {
        reset(msg);

        given(msg.makeRequest(any(), any())).willAnswer(__ -> newPromise());
        given(msg.makePrioritisedRequest(any(), any())).willAnswer(__ -> newPromise());

        server = new CoapServerBlocks(msg, capabilities, 100_000,
                RouterService.builder()
                        .get("/change", __ -> completedFuture(CoapResponse.ok("")))
                        .put("/change", req -> {
                            receivedPayload.set(req.getPayload());
                            return completedFuture(CoapResponse.of(Code.C204_CHANGED));
                        })
                        .get("/large", __ -> completedFuture(CoapResponse.ok(opaqueOfSize(2000))))
                        .get("/xlarge", __ -> completedFuture(CoapResponse.ok(opaqueOfSize(10000))))
                        .build());
        server.start();

        ArgumentCaptor<CoapRequestHandler> requestHandlerCaptor = ArgumentCaptor.forClass(CoapRequestHandler.class);
        verify(msg).start(requestHandlerCaptor.capture());
        requestHandler = requestHandlerCaptor.getValue();
    }

    private CompletableFuture<CoapPacket> newPromise() {
        promise = new CompletableFuture<>();
        return promise;
    }

    @Test
    public void shouldMakeNonBlockingRequest() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(1).get().uriPath("/test").build();

        CompletableFuture<CoapPacket> resp = server.makeRequest(req);

        verify(msg).makeRequest(eq(req), any());
        assertFalse(resp.isDone());
    }

    @Test
    public void shouldReceiveNonBlockingResponse() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(2).get().uriPath("/test").build();
        CoapPacket resp = newCoapPacket(LOCAL_5683).mid(2).con().payload("OK").build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        assertFalse(respFut.isDone());

        //verify response
        promise.complete(resp);

        assertEquals(resp, respFut.get());
    }

    @Test
    public void shouldMakeBlockingRequest_maxMsgSz20() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").build();
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").size1(32).block1Req(0, BlockSize.S_16, true)
        );

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        assertMakePriRequest(
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
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequestAndReceive(
                newCoapPacket(LOCAL_5683).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_1024, true).payload(opaqueOfSize(1024))
        );

        //BLOCK 1
        assertMakePriRequestAndReceive(
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).ack(Code.C205_CONTENT).payload(opaqueOfSize(1000))
        );


        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(respFut::get).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void shoudFail_toReceive_responseWithIncorrectLastBlockSize() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0123456789ABCDEF").build());

        //BLOCK 1
        assertMakePriRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

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
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("LARGE___PAYLOAD_").build());

        //BLOCK 1
        assertMakePriRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

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
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/status").build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status"));

        //response
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, S_1024_BERT, true).payload(opaqueOfSize(3072)).build());

        //BLOCK 1
        assertMakePriRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(3, S_1024_BERT, false));

        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(3, S_1024_BERT, true).payload(opaqueOfSize(5120)).build());

        //BLOCK 2
        assertMakePriRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(8, S_1024_BERT, false));

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

        CoapPacket req = newCoapPacket(LOCAL_5683).put().uriPath("/options").payload(opaqueOfSize(8192 + 8192 + 5683)).build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(0, S_1024_BERT, true).payload(opaqueOfSize(8192)).size1(22067));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(0, S_1024_BERT, true).build());

        //BLOCK 1
        assertMakePriRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(8, S_1024_BERT, true).payload(opaqueOfSize(8192)));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(8, S_1024_BERT, true).build());

        //BLOCK 2
        assertMakePriRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(16, S_1024_BERT, false).payload(opaqueOfSize(5683)));

        clearInvocations(msg);
        promise.complete(newCoapPacket().ack(Code.C204_CHANGED).block1Req(16, S_1024_BERT, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldFailSendBlockingRequest_when_blockTransferIsDisabled() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2

        CoapPacket req = newCoapPacket(LOCAL_5683).put().uriPath("/options").payload(opaqueOfSize(8192 + 8192 + 5683)).build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(() -> respFut.get())
                .hasCause(new CoapException("Block transfers are not enabled for localhost/127.0.0.1:5683 and payload size 22067 > max payload size 1152"));

        verify(msg, never()).makeRequest(any(), any());
        verify(msg, never()).makePrioritisedRequest(any(), any());
    }

    @Test
    public void should_send_blocs_with_different_tokens() {
        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(opaqueOfSize(16)).block1Req(0, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(1001).block1Req(0, BlockSize.S_16, true));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).put().token(2002).uriPath("/change").payload(opaqueOfSize(16)).block1Req(1, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(2002).block1Req(1, BlockSize.S_16, true));

        //BLOCK 3
        receive(newCoapPacket(LOCAL_5683).put().token(3003).uriPath("/change").payload(opaqueOfSize(1)).block1Req(2, BlockSize.S_16, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).token(3003).block1Req(2, BlockSize.S_16, false));


        assertEquals(33, receivedPayload.get().size());
    }

    @Test
    public void should_send_error_when_wrong_first_payload_and_block_size() throws Exception {
        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(opaqueOfSize(17)).block1Req(0, BlockSize.S_16, true));

        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C400_BAD_REQUEST).token(1001).payload("block size mismatch"));
    }

    @Test
    public void should_send_error_when_wrong_second_payload_and_block_size() throws Exception {
        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(opaqueOfSize(16)).block1Req(0, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(1001).block1Req(0, BlockSize.S_16, true));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(opaqueOfSize(17)).block1Req(1, BlockSize.S_16, true));

        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C400_BAD_REQUEST).token(1001).payload("block size mismatch"));
    }

    @Test
    public void should_continue_block_transfer_after_block_size_change() throws ExecutionException, InterruptedException {
        CoapPacket req = newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_LARGE___PAYLOAD").build();
        capabilities.put(LOCAL_5683, new CoapTcpCSM(40, true));

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").size1(47).block1Req(0, BlockSize.S_32, true)
        );

        //response new size=16
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        assertMakePriRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, true)
        );
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(1, BlockSize.S_16, false).build());

        //BLOCK 2
        assertMakePriRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD").block1Req(2, BlockSize.S_16, false)
        );
        clearInvocations(msg);
        promise.complete(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldSendBlockingResponse_2k_with_BERT() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1200, true));

        //BLOCK 0
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large"));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(1024)));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large").block2Res(1, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(1, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(976)));
    }

    @Test
    public void shouldSendBlockingResponse_2k_no_BERT_needed() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(4000, true));

        //when
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large"));

        //then full payload, no blocks
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).payload(opaqueOfSize(2000)));
    }

    @Test
    public void shouldSendBlockingResponse_10k_with_BERT() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(6000, true));

        //BLOCK 0
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge"));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(0, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge").block2Res(4, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(4, BlockSize.S_1024_BERT, true).payload(opaqueOfSize(4096)));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge").block2Res(8, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(8, BlockSize.S_1024_BERT, false).payload(opaqueOfSize(1808)));
    }


    private void assertSent(CoapPacketBuilder resp) {
        verify(msg).sendResponse(any(), eq(resp.build()), any());
    }

    private void receive(CoapPacketBuilder req) {
        requestHandler.handleRequest(req.build(), TransportContext.NULL);
    }


    //--------------------------

    private void assertMakeRequest(CoapPacketBuilder req) {
        assertMakeRequest(req.build());
    }

    private void assertMakeRequest(CoapPacket req) {
        verify(msg).makeRequest(eq(req), any());
    }

    private void assertMakePriRequest(CoapPacketBuilder req) {
        verify(msg).makePrioritisedRequest(eq(req.build()), any());
    }

    private void assertMakeRequestAndReceive(CoapPacketBuilder req, CoapPacketBuilder receive) {
        assertMakeRequest(req);

        //response
        clearInvocations(msg);
        assertTrue(promise.complete(receive.build()));
    }

    private void assertMakePriRequestAndReceive(CoapPacketBuilder req, CoapPacketBuilder receive) {
        assertMakePriRequest(req);

        //response
        clearInvocations(msg);
        assertTrue(promise.complete(receive.build()));
    }

}