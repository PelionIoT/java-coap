/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
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
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import protocolTests.utils.CoapPacketBuilder;

class CoapTcpDispatcherTest {

    private Service<CoapPacket, Boolean> sender = Mockito.mock(Service.class);
    private Service<CoapRequest, CoapResponse> inboundService = Mockito.mock(Service.class);
    private Function<SeparateResponse, Boolean> outboundHandler = Mockito.mock(Function.class);
    private Function<SeparateResponse, Boolean> observationHandler = Mockito.mock(Function.class);
    private CoapTcpCSMStorage csmStorage = new CoapTcpCSMStorageImpl();

    private CoapTcpDispatcher dispatcher = new CoapTcpDispatcher(sender, csmStorage, new CoapTcpCSM(501, false), inboundService, outboundHandler, observationHandler);

    @BeforeEach
    void setUp() {
        given(outboundHandler.apply(any())).willReturn(true);
        given(observationHandler.apply(any())).willReturn(true);
        given(sender.apply(any())).willReturn(completedFuture(true));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(sender, inboundService, outboundHandler, observationHandler);
    }

    @Test
    void ignoreEmptyMessage() {
        dispatcher.handle(newCoapPacket(LOCAL_1_5683).build(), TransportContext.EMPTY);

        verifyNoInteractions(sender);
    }

    @Test
    public void should_receive_response() {
        CoapPacket packet = newCoapPacket(LOCAL_1_5683).token(2001).code(Code.C205_CONTENT).build();
        dispatcher.handle(packet, TransportContext.EMPTY);

        verify(outboundHandler).apply(eq(CoapResponse.of(Code.C205_CONTENT).toSeparate(variableUInt(2001), LOCAL_1_5683)));
    }

    @Test
    public void should_receive_response_toObservation() {
        CoapPacket packet = newCoapPacket(LOCAL_1_5683).token(2001).code(Code.C205_CONTENT).obs(12).build();
        dispatcher.handle(packet, TransportContext.EMPTY);

        verify(outboundHandler).apply(eq(CoapResponse.of(Code.C205_CONTENT).observe(12).toSeparate(variableUInt(2001), LOCAL_1_5683)));
    }

    @Test
    public void should_receive_observation() throws Exception {
        given(outboundHandler.apply(any())).willReturn(false);

        receive(newCoapPacket(LOCAL_1_5683).token(2001).code(Code.C205_CONTENT).obs(12).payload("21C"));

        verify(observationHandler).apply(ok("21C").observe(12).toSeparate(variableUInt(2001), LOCAL_1_5683));
        verify(outboundHandler).apply(any());
    }

    @Test
    public void should_ignore_non_matching_response() {
        given(outboundHandler.apply(any())).willReturn(false);

        receive(newCoapPacket(LOCAL_1_5683).token(1002).code(Code.C205_CONTENT));
        verify(outboundHandler).apply(any());
        verify(observationHandler, never()).apply(any());
    }

    @Test
    public void should_pass_request_to_inbound_service() {
        given(inboundService.apply(any())).willReturn(completedFuture(ok("OK")));
        CoapPacketBuilder req = newCoapPacket(LOCAL_1_5683).token(2002).get().uriPath("/some/request");

        receive(req);

        assertSent(newCoapPacket(LOCAL_1_5683).ack(Code.C205_CONTENT).token(2002).payload("OK"));
        verify(inboundService).apply(eq(get(LOCAL_1_5683, "/some/request").token(2002)));
    }

    @Test
    public void should_replay_pong_to_ping() throws Exception {
        receive(newCoapPacket(LOCAL_1_5683).token(2004).code(Code.C702_PING));

        assertSent(newCoapPacket(LOCAL_1_5683).token(2004).code(Code.C703_PONG));
    }


    @Test
    public void should_call_disconnected_to_outbound() throws Exception {
        // when
        dispatcher.onDisconnected(LOCAL_5683);

        verify(outboundHandler).apply(eq(CoapResponse.of(Code.C705_ABORT).toSeparate(Opaque.EMPTY, LOCAL_5683)));
    }

    @Test
    public void should_call_disconnected_to_outbound_when_abort_signal() throws Exception {
        // when
        receive(newCoapPacket(LOCAL_5683).non(Code.C705_ABORT));

        verify(outboundHandler).apply(eq(CoapResponse.of(Code.C705_ABORT).toSeparate(Opaque.EMPTY, LOCAL_5683)));
    }

    @Test
    public void should_set_minimal_remote_capabilities_fromLarge() throws CoapException, IOException {
        SignalingOptions signOpt = SignalingOptions.capabilities(10000, true);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM).signalling(signOpt));

        assertEquals(501, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_set_minimal_remote_capabilities_fromSmall() throws CoapException, IOException {
        SignalingOptions signOpt = SignalingOptions.capabilities(300, false);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM).signalling(signOpt));

        assertEquals(300, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_set_remote_capabilities_when_empty_csm() throws CoapException, IOException {
        receive(newCoapPacket(LOCAL_1_5683).con(Code.C701_CSM));

        assertEquals(501, csmStorage.getOrDefault(LOCAL_1_5683).getMaxMessageSize());
        assertFalse(csmStorage.getOrDefault(LOCAL_1_5683).isBlockTransferEnabled());
    }

    @Test
    public void should_handle_pong() throws Exception {
        // when
        CoapPacket packet = newCoapPacket(LOCAL_5683).code(Code.C703_PONG).build();
        dispatcher.handle(packet, TransportContext.EMPTY);

        // then
        verify(outboundHandler).apply(eq(CoapResponse.of(Code.C703_PONG).toSeparate(Opaque.EMPTY, LOCAL_5683)));

    }

    @Test
    public void shouldSendCapabilities_whenConnected_noBlocking() throws CoapException, IOException {
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(501);
        signOpt.setBlockWiseTransfer(false);

        //when
        dispatcher.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    @Test
    public void shouldSendCapabilities_whenConnected_withBlocking() throws CoapException, IOException {
        dispatcher = new CoapTcpDispatcher(sender, csmStorage, new CoapTcpCSM(10501, true), inboundService, outboundHandler, observationHandler);
        SignalingOptions signOpt = new SignalingOptions();
        signOpt.setMaxMessageSize(10501);
        signOpt.setBlockWiseTransfer(true);

        //when
        dispatcher.onConnected(LOCAL_5683);

        //then
        assertSent(newCoapPacket(LOCAL_5683).code(Code.C701_CSM).signalling(signOpt));
    }

    @Test
    void shouldIgnoreUnexpectedMessage() throws Exception {
        given(outboundHandler.apply(any())).willReturn(false);

        receive(newCoapPacket(LOCAL_1_5683).con(Code.C205_CONTENT).payload("dupa"));

        verify(outboundHandler).apply(any());
    }


    private void receive(CoapPacketBuilder coapPacketBuilder) {
        dispatcher.handle(coapPacketBuilder.build(), TransportContext.EMPTY);
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) {
        assertSent(coapPacketBuilder.build());
    }

    private void assertSent(CoapPacket coapPacket) {
        verify(sender).apply(eq(coapPacket));
    }

}