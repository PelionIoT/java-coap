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

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import protocolTests.utils.CoapPacketBuilder;

class PiggybackedExchangeFilterTest {
    private final PiggybackedExchangeFilter transactionFilter = new PiggybackedExchangeFilter();
    private final Service<CoapPacket, Boolean> sendService = Mockito.mock(Service.class);
    private final Function<CoapPacketBuilder, CompletableFuture<CoapPacket>> service = transactionFilter
            .then(sendService)
            .compose(CoapPacketBuilder::build);

    private CompletableFuture<CoapPacket> resp;
    private CompletableFuture<CoapPacket> resp2;

    @BeforeEach
    void setUp() {
        reset(sendService);
        given(sendService.apply(any())).willReturn(completedFuture(true));
    }


    @Test
    void shouldHandleAck() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // when
        assertTrue(transactionFilter.handleResponse(newCoapPacket(LOCAL_5683).emptyAck(12)));

        // then
        assertEquals(newCoapPacket(LOCAL_5683).emptyAck(12), resp.join());

        assertSent(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldHandleAckForSeparateResponse() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).con(Code.C205_CONTENT).payload("ok"));

        // when
        assertTrue(transactionFilter.handleResponse(newCoapPacket(LOCAL_5683).emptyAck(12)));

        // then
        assertEquals(newCoapPacket(LOCAL_5683).emptyAck(12), resp.join());
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldOnlyForwardNonMessage() {
        // when
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).non().get().uriPath("/test"));

        // then
        assertFalse(resp.isDone());
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldHandleReset() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // when
        assertTrue(transactionFilter.handleResponse(newCoapPacket(LOCAL_5683).reset(12)));

        // then
        assertEquals(newCoapPacket(LOCAL_5683).reset(12), resp.join());

        assertSent(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldFailWhenTransportFails() {
        // given
        given(sendService.apply(any())).willReturn(failedFuture(new IOException()));

        // when
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // then
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(IOException.class);

        assertSent(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldRejectNonMatchingResponse() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // when
        assertFalse(transactionFilter.handleResponse(
                newCoapPacket(LOCAL_1_5683).emptyAck(0))
        );
        assertFalse(transactionFilter.handleResponse(
                newCoapPacket(LOCAL_5683).emptyAck(1423))
        );
        assertFalse(transactionFilter.handleResponse(
                newCoapPacket(LOCAL_5683).mid(0).con(Code.C205_CONTENT).token(1234).payload("ok").build())
        );
        assertFalse(transactionFilter.handleResponse(
                newCoapPacket(LOCAL_5683).mid(0).non(Code.C205_CONTENT).token(1234).payload("ok").build())
        );

        // then
        assertFalse(resp.isDone());
        verify(sendService).apply(any());
        assertEquals(1, transactionFilter.transactions());
    }


    @Test
    void shouldSendRetransmissionAndThenReceiveAck() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));
        resp2 = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // when
        assertTrue(transactionFilter.handleResponse(newCoapPacket(LOCAL_5683).emptyAck(12)));

        // then
        assertEquals(newCoapPacket(LOCAL_5683).emptyAck(12), resp.join());
        assertEquals(newCoapPacket(LOCAL_5683).emptyAck(12), resp2.join());

        assertSent(2, newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldRemoveTransactionWhenPromiseIsCancelled() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).get().uriPath("/test"));

        // when
        resp.cancel(false);

        // then
        assertFalse(transactionFilter.handleResponse(newCoapPacket(LOCAL_5683).emptyAck(12)));
        assertEquals(0, transactionFilter.transactions());
    }

    @Test
    void shouldThrowWhenStopped() {
        // given
        resp = service.apply(newCoapPacket(LOCAL_5683).mid(12).con(Code.C205_CONTENT).payload("ok"));

        // when
        transactionFilter.stop();

        // then
        assertTrue(resp.isCompletedExceptionally());
    }

    private void assertSent(CoapPacketBuilder expected) {
        assertSent(1, expected);
    }

    private void assertSent(int numberOfSending, CoapPacketBuilder expected) {
        verify(sendService, times(numberOfSending)).apply(eq(expected.build()));
    }
}