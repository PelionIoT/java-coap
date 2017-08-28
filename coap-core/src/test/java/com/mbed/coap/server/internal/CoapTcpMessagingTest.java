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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;


/*
    draft-ietf-core-coap-tcp-tls-09
 */
public class CoapTcpMessagingTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    CoapTcpCSMStorageImpl csmStorage = new CoapTcpCSMStorageImpl();
    CoapTcpMessaging tcpMessaging = new CoapTcpMessaging(coapTransport, csmStorage, null);
    CoapRequestHandler coapRequestHandler = mock(CoapRequestHandler.class);

    @Before
    public void setUp() throws Exception {
        reset(coapRequestHandler);

        tcpMessaging.start(coapRequestHandler);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(true));
    }

    @After
    public void tearDown() throws Exception {
        tcpMessaging.stop();
    }

    @Test
    public void should_receive_response_to_request() throws Exception {
        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test"));

        receive(newCoapPacket(LOCAL_1_5683).token(2001).ack(Code.C205_CONTENT));
        assertNotNull(resp.get());
    }

    @Test
    public void should_ignore_non_matching_response() throws Exception {
        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test"));

        receive(newCoapPacket(LOCAL_1_5683).token(1002).ack(Code.C205_CONTENT));
        assertFalse(resp.isDone());
    }

    @Test
    public void should_pass_request_to_request_handler() throws Exception {
        CoapPacketBuilder req = newCoapPacket(LOCAL_1_5683).token(2002).get().uriPath("/some/request");

        receive(req);

        verify(coapRequestHandler).handleRequest(eq(req.build()), any());
        assertNothingSent();
    }

    @Test
    public void should_fail_to_make_request_when_transport_fails() throws Exception {
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(completedFuture(new IOException()));

        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test"));

        assertTrue(resp.isCompletedExceptionally());
    }

    //https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4
    @Test
    public void should_not_reply_to_empty_message() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683));

        assertNothingSent();
    }

    @Test
    public void should_replay_pong_to_ping() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).token(2004).code(Code.C702_PING));

        assertSent(newCoapPacket(LOCAL_1_5683).token(2004).ack(Code.C703_PONG));
    }

    @Test
    public void should_call_exception_when_disconnected() throws Exception {
        CompletableFuture<CoapPacket> resp1 = makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test"));
        CompletableFuture<CoapPacket> resp2 = makeRequest(newCoapPacket(LOCAL_1_5683).token(2002).con().get().uriPath("/test2"));

        tcpMessaging.onDisconnected(LOCAL_1_5683);

        assertTrue(resp1.isCompletedExceptionally());
        assertThatThrownBy(resp1::get).hasCauseExactlyInstanceOf(IOException.class);
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void should_call_exception_when_abort_signal_received() throws Exception {
        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_1_5683).token(2001).con().get().uriPath("/test"));
        reset(coapTransport);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C705_ABORT));

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
        assertNothingSent();
    }

    @Test
    public void should_set_remote_capability() throws CoapException, IOException {
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(211);
        signOpt.setBlockWiseTransfer(false);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM).signalling(signOpt));

        assertNothingSent();
        assertEquals(211, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_send_ping_message() throws Exception {
        tcpMessaging.ping(LOCAL_1_5683, mock(Callback.class));

        assertSent(new CoapPacket(Code.C702_PING, null, LOCAL_1_5683));
    }

    @Test
    public void should_handle_pong() throws Exception {
        FutureCallbackAdapter<CoapPacket> resp = new FutureCallbackAdapter<>();

        tcpMessaging.ping(LOCAL_1_5683, resp);
        receive(newCoapPacket(LOCAL_1_5683).code(Code.C703_PONG));

        assertEquals(Code.C703_PONG, resp.get().getCode());
    }

    @Test
    public void shouldSendCapabilities_whenConnected_noBlocking() throws CoapException, IOException {
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(501);
        signOpt.setBlockWiseTransfer(false);
        tcpMessaging.setLocalMaxMessageSize(501);

        //when
        tcpMessaging.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    @Test
    public void shouldSendCapabilities_whenConnected_withBlocking() throws CoapException, IOException {
        tcpMessaging = new CoapTcpMessaging(coapTransport, csmStorage, BlockSize.S_1024_BERT);
        tcpMessaging.start(coapRequestHandler);
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(10501);
        signOpt.setBlockWiseTransfer(true);
        tcpMessaging.setLocalMaxMessageSize(10501);

        //when
        tcpMessaging.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    //=======================================================================

    private static CompletableFuture<Boolean> completedFuture(IOException exception) {
        CompletableFuture f = new CompletableFuture();
        f.completeExceptionally(exception);
        return f;
    }

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        tcpMessaging.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacket), any(), any());
    }

    private void assertNothingSent() throws CoapException, IOException {
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    private CompletableFuture<CoapPacket> makeRequest(CoapPacketBuilder coapPacket) {
        FutureCallbackAdapter<CoapPacket> completableFuture = new FutureCallbackAdapter<>();

        tcpMessaging.makeRequest(coapPacket.build(), completableFuture, TransportContext.NULL);
        return completableFuture;
    }

}
