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

import static com.mbed.coap.packet.BlockSize.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Callback;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerBlocksUnitTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CoapServerBlocks server;

    @Before
    public void setUp() throws Exception {
        reset(msg);
        server = new CoapServerBlocks(msg);

        when(msg.getBlockSize(any())).thenReturn(BlockSize.S_16);
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
    public void shouldMakeBlockingRequest() throws Exception {
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).post().uriPath("/test").payload("LARGE___PAYLOAD_LARGE___PAYLOAD_").build();
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
    @Ignore
    public void shouldReceiveBlockingResponse_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.1
        when(msg.getBlockSize(any())).thenReturn(S_1024_BERT);
        Callback<CoapPacket> callback;
        CoapPacket req = newCoapPacket(LOCAL_5683).get().uriPath("/status").build();

        CompletableFuture<CoapPacket> respFut = server.makeRequest(req);

        //BLOCK 0
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status"));

        //response
        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(0, S_1024_BERT, true).payload(new byte[3072]).build());

        //BLOCK 1
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(3, S_1024_BERT, false));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(3, S_1024_BERT, true).payload(new byte[5120]).build());

        //BLOCK 2
        callback = assertMakeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/status").block2Res(8, S_1024_BERT, false));

        reset(msg);
        callback.call(newCoapPacket(LOCAL_5683).ack(Code.C205_CONTENT).block2Res(8, S_1024_BERT, false).payload(new byte[4711]).build());


        //verify
        assertTrue(respFut.isDone());
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
        assertEquals(3072 + 5120 + 4711, respFut.get().getPayload().length);
    }

    @Test
    @Ignore
    public void shouldSendBlockingRequest_with_BERT() throws Exception {
        //based on https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-6.2
        when(msg.getBlockSize(any())).thenReturn(S_1024_BERT);
        when(msg.getMaxMessageSize(any())).thenReturn(10000);

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
        assertEquals(Code.C205_CONTENT, respFut.get().getCode());
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

}