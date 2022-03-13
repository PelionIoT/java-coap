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

import static java.util.concurrent.CompletableFuture.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;


public class CoapDispatcherTest {

    private final Service<CoapPacket, Boolean> sender = mock(Service.class);
    private Service<CoapPacket, CoapPacket> observationHandler = mock(Service.class);
    private Function<CoapPacket, Boolean> handleResponse = mock(Function.class);
    private Function<SeparateResponse, Boolean> handleSeparateResponse = mock(Function.class);
    private CoapDispatcher udpMessaging;
    private Service<CoapPacket, CoapPacket> inboundService = mock(Service.class);


    @BeforeEach
    public void setUp() throws Exception {
        reset(sender, observationHandler, handleResponse, handleSeparateResponse);

        given(sender.apply(any())).willReturn(completedFuture(null));
        given(observationHandler.apply(any())).willReturn(completedFuture(mock(CoapPacket.class)));
        given(handleResponse.apply(any())).willReturn(false);
        given(handleSeparateResponse.apply(any())).willReturn(false);

        udpMessaging = new CoapDispatcher(sender, observationHandler, inboundService, handleResponse, handleSeparateResponse);
    }

    @Test
    public void responseToPingMessage() throws Exception {
        handle(newCoapPacket(LOCAL_1_5683).mid(1).con(null));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    void sendResetToUnexpectedMessage() throws Exception {
        handle(newCoapPacket(LOCAL_1_5683).mid(1).con(Code.C205_CONTENT).payload("dupa"));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    void sendResetToUnexpectedNonPacket() throws Exception {
        handle(newCoapPacket(LOCAL_1_5683).mid(9156).non(Code.C205_CONTENT).payload("dupa"));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(9156).reset());
    }

    @Test
    public void ignore_nonProcessedMessage() throws Exception {
        handle(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C203_VALID));
        verify(sender, never()).apply(any());

        udpMessaging.handle(newCoapPacket(LOCAL_1_5683).reset(1));
        verify(sender, never()).apply(any());
    }

    @Test
    public void handleInboundRequest() throws Exception {
        CoapPacket resp = newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0").build();
        given(inboundService.apply(any())).willReturn(completedFuture(resp));

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();
        udpMessaging.handle(req);

        assertSent(resp);
    }

    @Test
    public void receiveObservation() throws Exception {
        handle(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));
        handle(newCoapPacket(LOCAL_5683).mid(3002).obs(2).con(Code.C205_CONTENT).token(44).payload("B"));

        verify(observationHandler, times(2)).apply(any());
    }

    @Test
    public void receiveConSeparateResponse() throws CoapException, IOException {
        given(handleSeparateResponse.apply(any())).willReturn(true);

        handle(newCoapPacket(LOCAL_5683).mid(3001).con(Code.C203_VALID).token(432).payload("ok"));

        assertSent(newCoapPacket(LOCAL_5683).emptyAck(3001));
    }

    @Test
    public void receiveNonSeparateResponse() throws CoapException, IOException {
        given(handleSeparateResponse.apply(any())).willReturn(true);

        handle(newCoapPacket(LOCAL_5683).mid(3001).non(Code.C203_VALID).token(432).payload("ok"));

        verify(sender, never()).apply(any());
    }

    private void handle(CoapPacketBuilder coapPacketBuilder) {
        udpMessaging.handle(coapPacketBuilder.build());
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(sender).apply(eq(coapPacket));
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

}
