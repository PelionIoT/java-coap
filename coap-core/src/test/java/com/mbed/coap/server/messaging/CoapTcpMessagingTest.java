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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.utils.Bytes.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;

public class CoapTcpMessagingTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    CoapTcpCSMStorageImpl csmStorage = new CoapTcpCSMStorageImpl();
    CoapTcpMessaging tcpMessaging = new CoapTcpMessaging(coapTransport, csmStorage, false, 501);
    Function<SeparateResponse, Boolean> observationHandler = mock(Function.class);
    Service<CoapRequest, CoapResponse> requestService = req -> completedFuture(ok("OK"));

    @BeforeEach
    public void setUp() throws Exception {
        reset(observationHandler);
        given(observationHandler.apply(any())).willReturn(true);

        tcpMessaging.init(observationHandler, requestService);
        tcpMessaging.start();
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(completedFuture(true));
    }

    @AfterEach
    public void tearDown() {
        tcpMessaging.stop();
    }

    @Test
    public void should_receive_response_to_request() throws Exception {
        CompletableFuture<CoapResponse> resp = tcpMessaging.send(get(LOCAL_1_5683, "/test").token(2001));

        receive(newCoapPacket(LOCAL_1_5683).token(2001).ack(Code.C205_CONTENT));
        assertNotNull(resp.get());
    }

    @Test
    public void should_receive_observation() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).token(2001).code(Code.C205_CONTENT).obs(12).payload("21C"));

        verify(observationHandler).apply(ok("21C").observe(12).toSeparate(Opaque.variableUInt(2001), LOCAL_1_5683));
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void should_ignore_non_matching_response() throws Exception {
        CompletableFuture<CoapResponse> resp = tcpMessaging.send(get(LOCAL_1_5683, "/test").token(2001));

        receive(newCoapPacket(LOCAL_1_5683).token(1002).ack(Code.C205_CONTENT));
        assertFalse(resp.isDone());
    }

    @Test
    public void should_pass_request_to_request_handler() throws Exception {
        CoapPacketBuilder req = newCoapPacket(LOCAL_1_5683).mid(123).token(2002).get().uriPath("/some/request");

        receive(req);

        assertSent(newCoapPacket(LOCAL_1_5683).mid(123).ack(Code.C205_CONTENT).token(2002).payload("OK"));
    }

    @Test
    public void should_fail_to_make_request_when_transport_fails() throws Exception {
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(failedFuture(new IOException()));

        CompletableFuture<CoapResponse> resp = tcpMessaging.send(get(LOCAL_1_5683, "/test").token(2001));

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
        CompletableFuture<CoapResponse> resp1 = tcpMessaging.send(get(LOCAL_1_5683, "/test").token(2001));
        CompletableFuture<CoapResponse> resp2 = tcpMessaging.send(get(LOCAL_1_5683, "/test2").token(2002));

        tcpMessaging.onDisconnected(LOCAL_1_5683);

        assertTrue(resp1.isCompletedExceptionally());
        assertThatThrownBy(resp1::get).hasCauseExactlyInstanceOf(IOException.class);
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void should_call_exception_when_abort_signal_received() throws Exception {
        CompletableFuture<CoapResponse> resp = tcpMessaging.send(get(LOCAL_1_5683, "/test").token(2001));
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
        // when
        tcpMessaging.send(ping(LOCAL_1_5683, TransportContext.EMPTY));

        assertSent(new CoapPacket(Code.C702_PING, null, LOCAL_1_5683));
    }

    @Test
    public void should_handle_pong() throws Exception {
        // when
        CompletableFuture<CoapResponse> resp = tcpMessaging.send(ping(LOCAL_1_5683, TransportContext.EMPTY));
        receive(newCoapPacket(LOCAL_1_5683).code(Code.C703_PONG));

        assertSent(new CoapPacket(Code.C702_PING, null, LOCAL_1_5683));
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
        tcpMessaging.init(observationHandler, requestService);
        tcpMessaging.start();
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
        CompletableFuture<CoapResponse> resp = tcpMessaging.send(put(LOCAL_5683, "/").payload(opaqueOfSize(1200)));

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapException.class);
    }

    @Test
    void shouldSendObservation() throws CoapException, IOException {
        CompletableFuture<Boolean> ack = tcpMessaging.send(ok("21C").observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));

        assertSent(newCoapPacket(LOCAL_1_5683).token(1003).con(Code.C205_CONTENT).obs(12).payload("21C"));
        assertTrue(ack.join());
    }

    @Test
    void shouldFailToSendObservation_when_transportFails() throws CoapException, IOException {
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(failedFuture(new IOException()));

        CompletableFuture<Boolean> ack = tcpMessaging.send(ok("21C").observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));

        assertThatThrownBy(ack::join).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void shouldFailToSendObservation_when_tooLargePayload() {
        CompletableFuture<Boolean> resp = tcpMessaging.send(ok(opaqueOfSize(1200)).observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));

        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapException.class);
    }

    @Test
    void shouldIgnoreUnexpectedMessage() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).con(Code.C205_CONTENT).payload("dupa"));

        verify(coapTransport, never()).sendPacket(any(), any(), any());
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

}
