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

import static com.mbed.coap.packet.BlockSize.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java8.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerBlocksUnitTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CoapServerBlocks server;
    private CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();
    private CoapRequestHandler requestHandler;

    @Before
    public void setUp() throws Exception {
        reset(msg);
        server = new CoapServerBlocks(msg, capabilities, 100_000);
        server.start();

        ArgumentCaptor<CoapRequestHandler> requestHandlerCaptor = ArgumentCaptor.forClass(CoapRequestHandler.class);
        verify(msg).start(requestHandlerCaptor.capture());
        requestHandler = requestHandlerCaptor.getValue();
    }

    @Test
    public void shouldMakeNonBlockingRequest() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(1).get().uriPath("/test").build();

        CompletableFuture<CoapPacket> resp = server.makeRequest(req);

        verify(msg).makeRequest(eq(req), any(), any());
        assertFalse(resp.isDone());
    }

    @Test
    public void shouldReceiveNonBlockingResponse() throws Exception {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(2).get().uriPath("/test").build();
        CoapPacket resp = newCoapPacket(LOCAL_5683).mid(2).con().payload("OK").build();
        Callback<CoapPacket> callback;

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        callback = assertMakeRequest(req);
        assertFalse(respFut.isDone());

        //verify response
        callback.call(resp);

        assertEquals(resp, respFut.get());
    }

    @Test
    public void shouldMakeBlockingRequest_maxMsgSz20() throws Exception {
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").build();
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").size1(32).block1Req(0, BlockSize.S_16, true)
        );

        //response
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        callback = assertMakePriRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, false)
        );
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldFail_toReceive_tooLarge_blockingResponse() throws Exception {
        server = new CoapServerBlocks(msg, capabilities, 2000);
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        assertMakeRequestAndReceive(
                newCoapPacket(LOCAL_5683).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_1024, true).payload(new byte[1024])
        );

        //BLOCK 1
        assertMakePriRequestAndReceive(
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).get().uriPath("/test"),
                newCoapPacket(LOCAL_5683).block2Res(1, BlockSize.S_1024, false).ack(Code.C205_CONTENT).payload(new byte[1000])
        );


        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(respFut::get).hasCauseExactlyInstanceOf(CoapBlockTooLargeEntityException.class);
    }

    @Test
    public void shoudFail_toReceive_responseWithIncorrectLastBlockSize() {
        Callback<CoapPacket> callback;
        capabilities.put(LOCAL_5683, new CoapTcpCSM(20, true));

        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0123456789ABCDEF").build());

        //BLOCK 1
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("0123456789abcdef_").build());

        //verify
        assertTrue(respFut.isDone());
        assertThatThrownBy(() -> respFut.get())
                .hasCauseExactlyInstanceOf(CoapBlockException.class)
                .hasMessageStartingWith("com.mbed.coap.exception.CoapBlockException: Last block size mismatch with block option");
    }

    @Test
    public void shouldReceiveBlockingResponse() throws Exception {
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/test").build();
        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/test"));

        //response
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("LARGE___PAYLOAD_").build());

        //BLOCK 1
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).get().block2Res(1, BlockSize.S_16, false).uriPath("/test"));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("LARGE___PAYLOAD_").build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals("LARGE___PAYLOAD_LARGE___PAYLOAD_", respFut.get().getPayloadString());
    }

    @Test
    public void shouldReceiveBlockingResponse_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.1
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/status").build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status"));

        //response
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, S_1024_BERT, true).payload(new byte[3072]).build());

        //BLOCK 1
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(3, S_1024_BERT, false));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(3, S_1024_BERT, true).payload(new byte[5120]).build());

        //BLOCK 2
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(8, S_1024_BERT, false));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(8, S_1024_BERT, false).payload(new byte[4711]).build());


        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals(3072 + 5120 + 4711, respFut.get().getPayload().length);
    }

    @Test
    public void shouldSendBlockingRequest_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2
        capabilities.put(LOCAL_5683, new CoapTcpCSM(10000, true));

        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).put().uriPath("/options").payload(new byte[8192 + 8192 + 5683]).build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(0, S_1024_BERT, true).payload(new byte[8192]).size1(22067));

        reset(msg);
        callback.call(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(0, S_1024_BERT, true).build());

        //BLOCK 1
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(8, S_1024_BERT, true).payload(new byte[8192]));

        reset(msg);
        callback.call(newCoapPacket().ack(Code.C231_CONTINUE).block1Req(8, S_1024_BERT, true).build());

        //BLOCK 2
        callback = assertMakePriRequest(newCoapPacket(LOCAL_5683).put().uriPath("/options").block1Req(16, S_1024_BERT, false).payload(new byte[5683]));

        reset(msg);
        callback.call(newCoapPacket().ack(Code.C204_CHANGED).block1Req(16, S_1024_BERT, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldFailSendBlockingRequest_when_blockTransferIsDisabled() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2

        CoapPacket req = newCoapPacket(LOCAL_5683).put().uriPath("/options").payload(new byte[8192 + 8192 + 5683]).build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        assertTrue(respFut.isCompletedExceptionally());
        assertThatThrownBy(() -> respFut.get())
                .hasCause(new CoapException("Block transfers are not enabled for localhost/127.0.0.1:5683 and payload size 22067 > max payload size 1152"));

        verify(msg, never()).makeRequest(any(), any(), any());
        verify(msg, never()).makePrioritisedRequest(any(), any(), any());
    }

    @Test
    public void shouldThrowExceptionWhenCallBackIsNull() {
        final CoapPacket req = newCoapPacket(LOCAL_5683).put().uriPath("/options").payload(new byte[8192 + 8192 + 5683]).build();
        assertThatThrownBy(() ->
                server.makeRequest(req, null, TransportContext.NULL)
        ).isInstanceOf(NullPointerException.class).hasMessage("Callback must not be null");
    }

    @Test
    public void should_send_blocs_with_different_tokens() {
        final AtomicReference<byte[]> receivedPayload = new AtomicReference<>();
        server.addRequestHandler("/change", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) {

            }

            @Override
            public void put(CoapExchange exchange) {
                receivedPayload.set(exchange.getRequestBody());
                exchange.setResponseCode(Code.C204_CHANGED);
                exchange.sendResponse();
            }
        });


        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(new byte[16]).block1Req(0, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(1001).block1Req(0, BlockSize.S_16, true));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).put().token(2002).uriPath("/change").payload(new byte[16]).block1Req(1, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(2002).block1Req(1, BlockSize.S_16, true));

        //BLOCK 3
        receive(newCoapPacket(LOCAL_5683).put().token(3003).uriPath("/change").payload(new byte[1]).block1Req(2, BlockSize.S_16, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).token(3003).block1Req(2, BlockSize.S_16, false));


        assertEquals(33, receivedPayload.get().length);
    }

    @Test
    public void should_send_error_when_wrong_first_payload_and_block_size() throws Exception {
        server.addRequestHandler("/change", new ReadOnlyCoapResource(""));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(new byte[17]).block1Req(0, BlockSize.S_16, true));

        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C400_BAD_REQUEST).token(1001).payload("block size mismatch"));
    }

    @Test
    public void should_send_error_when_wrong_second_payload_and_block_size() throws Exception {
        server.addRequestHandler("/change", new ReadOnlyCoapResource(""));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(new byte[16]).block1Req(0, BlockSize.S_16, true));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).token(1001).block1Req(0, BlockSize.S_16, true));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).put().token(1001).uriPath("/change").payload(new byte[17]).block1Req(1, BlockSize.S_16, true));

        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C400_BAD_REQUEST).token(1001).payload("block size mismatch"));
    }

    @Test
    public void should_continue_block_transfer_after_block_size_change() throws ExecutionException, InterruptedException {
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_LARGE___PAYLOAD").build();
        capabilities.put(LOCAL_5683, new CoapTcpCSM(40, true));

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").size1(47).block1Req(0, BlockSize.S_32, true)
        );

        //response new size=16
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, false).build());


        //BLOCK 1
        callback = assertMakePriRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_").block1Req(1, BlockSize.S_16, true)
        );
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C231_CONTINUE).block1Req(1, BlockSize.S_16, false).build());

        //BLOCK 2
        callback = assertMakePriRequest(
                newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD").block1Req(2, BlockSize.S_16, false)
        );
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false).build());

        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C204_CHANGED, respFut.get().getCode());
    }

    @Test
    public void shouldSendBlockingResponse_2k_with_BERT() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(1200, true));
        server.addRequestHandler("/large", new ReadOnlyCoapResource(new String(new byte[2000])));

        //BLOCK 0
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large"));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(0, BlockSize.S_1024_BERT, true).payload(new byte[1024]));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large").block2Res(1, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(1, BlockSize.S_1024_BERT, false).payload(new byte[976]));
    }

    @Test
    public void shouldSendBlockingResponse_2k_no_BERT_needed() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(4000, true));
        server.addRequestHandler("/large", new ReadOnlyCoapResource(new String(new byte[2000])));

        //when
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/large"));

        //then full payload, no blocks
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).payload(new byte[2000]));
    }

    @Test
    public void shouldSendBlockingResponse_10k_with_BERT() throws Exception {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(6000, true));
        server.addRequestHandler("/xlarge", new ReadOnlyCoapResource(new String(new byte[10000])));

        //BLOCK 0
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge"));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(0, BlockSize.S_1024_BERT, true).payload(new byte[4096]));

        //BLOCK 1
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge").block2Res(4, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(4, BlockSize.S_1024_BERT, true).payload(new byte[4096]));

        //BLOCK 2
        receive(newCoapPacket(LOCAL_5683).get().token(1001).uriPath("/xlarge").block2Res(8, BlockSize.S_1024_BERT, false));
        assertSent(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).token(1001).block2Res(8, BlockSize.S_1024_BERT, false).payload(new byte[1808]));
    }


    private void assertSent(CoapPacketBuilder resp) {
        verify(msg).sendResponse(any(), eq(resp.build()), any());
    }

    private void receive(CoapPacketBuilder req) {
        requestHandler.handleRequest(req.build(), TransportContext.NULL);
    }


    //--------------------------

    private Callback<CoapPacket> assertMakeRequest(CoapPacketBuilder req) {
        return assertMakeRequest(req.build());
    }

    private Callback<CoapPacket> assertMakeRequest(CoapPacket req) {
        final ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);

        verify(msg).makeRequest(eq(req), callback.capture(), any());

        return callback.getValue();
    }

    private Callback<CoapPacket> assertMakePriRequest(CoapPacketBuilder req) {
        final ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);

        verify(msg).makePrioritisedRequest(eq(req.build()), callback.capture(), any());

        return callback.getValue();
    }

    private void assertMakeRequestAndReceive(CoapPacketBuilder req, CoapPacketBuilder receive) {
        Callback<CoapPacket> callback = assertMakeRequest(req);

        //response
        reset(msg);
        callback.call(receive.build());
    }

    private void assertMakePriRequestAndReceive(CoapPacketBuilder req, CoapPacketBuilder receive) {
        Callback<CoapPacket> callback = assertMakePriRequest(req);

        //response
        reset(msg);
        callback.call(receive.build());
    }

}