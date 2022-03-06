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

import static com.mbed.coap.utils.Bytes.*;
import static com.mbed.coap.utils.FutureHelpers.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;

public class CoapTcpMessagingTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    CoapTcpCSMStorageImpl csmStorage = new CoapTcpCSMStorageImpl();
    CoapTcpMessaging tcpMessaging = new CoapTcpMessaging(coapTransport, csmStorage, false, 501);
    CoapRequestHandler coapRequestHandler = mock(CoapRequestHandler.class);

    @BeforeEach
    public void setUp() throws Exception {
        reset(coapRequestHandler);

        tcpMessaging.start(coapRequestHandler);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(true));
    }

    @AfterEach
    public void tearDown() {
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
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(failedFuture(new IOException()));

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
    public void should_set_minimal_remote_capabilities_fromLarge() throws CoapException, IOException {
        SignalingOptions signOpt = SignalingOptions.capabilities(10000, true);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM).signalling(signOpt));

        assertNothingSent();
        assertEquals(501, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_set_minimal_remote_capabilities_fromSmall() throws CoapException, IOException {
        SignalingOptions signOpt = SignalingOptions.capabilities(300, false);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM).signalling(signOpt));

        assertNothingSent();
        assertEquals(300, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_set_remote_capabilities_when_empty_csm() throws CoapException, IOException {
        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM));

        assertNothingSent();
        assertEquals(501, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_send_ping_message() throws Exception {
        CoapPacket req = new CoapPacket(null, null, LOCAL_1_5683);

        // when
        tcpMessaging.makeRequest(req, TransportContext.EMPTY);

        assertSent(new CoapPacket(Code.C702_PING, null, LOCAL_1_5683));
    }

    @Test
    public void should_handle_pong() throws Exception {
        CoapPacket req = new CoapPacket(null, null, LOCAL_1_5683);

        // when
        CompletableFuture<CoapPacket> resp = tcpMessaging.makeRequest(req, TransportContext.EMPTY);
        receive(newCoapPacket(LOCAL_1_5683).code(Code.C703_PONG));

        assertEquals(Code.C703_PONG, resp.get().getCode());
    }

    @Test
    public void shouldSendCapabilities_whenConnected_noBlocking() throws CoapException, IOException {
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(501);
        signOpt.setBlockWiseTransfer(false);

        //when
        tcpMessaging.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    @Test
    public void shouldSendCapabilities_whenConnected_withBlocking() throws CoapException, IOException {
        tcpMessaging = new CoapTcpMessaging(coapTransport, csmStorage, true, 10501);
        tcpMessaging.start(coapRequestHandler);
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(10501);
        signOpt.setBlockWiseTransfer(true);

        //when
        tcpMessaging.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    @Test
    public void shouldThrowExceptionWhenTooLargePayload() {
        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_5683).get().payload(opaqueOfSize(1200)));

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapException.class);
    }

    @Test
    public void should_call_handler_when_connected() {
        Consumer<InetSocketAddress> handler = mock(Consumer.class);
        tcpMessaging.setConnectHandler(handler);

        tcpMessaging.onConnected(LOCAL_1_5683);
        verify(handler).accept(eq(LOCAL_1_5683));

        tcpMessaging.onConnected(LOCAL_5683);
        verify(handler).accept(eq(LOCAL_5683));
    }

    //=======================================================================

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        tcpMessaging.handle(coapPacketBuilder.build(), TransportContext.EMPTY);
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
        return tcpMessaging.makeRequest(coapPacket.build(), TransportContext.EMPTY);
    }

}
