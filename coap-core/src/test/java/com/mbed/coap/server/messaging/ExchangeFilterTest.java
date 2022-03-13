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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExchangeFilterTest {

    private final ExchangeFilter exchangeFilter = new ExchangeFilter();
    private final Service<CoapRequest, CoapResponse> outbound = Mockito.mock(Service.class);
    private final Service<CoapRequest, CoapResponse> service = exchangeFilter
            .then(outbound);

    private CompletableFuture<CoapResponse> promise;
    private CompletableFuture<CoapResponse> resp;

    @BeforeEach
    void setUp() {
        reset(outbound);
        promise = new CompletableFuture<>();
        given(outbound.apply(any())).willReturn(promise);

    }

    @Test
    void piggybackedExchange() {
        // given
        CoapRequest req = CoapRequest.get(LOCAL_5683, "/13");
        resp = service.apply(req);
        assertEquals(1, exchangeFilter.transactions());

        // when
        CoapResponse cpResp = CoapResponse.ok("ok");
        promise.complete(cpResp);

        // then
        assertEquals(cpResp, resp.join());
        verify(outbound).apply(req);
        assertEquals(0, exchangeFilter.transactions());
    }

    @Test
    void shouldHandleSeparateResponse() {
        // given
        resp = service.apply(CoapRequest.get(LOCAL_5683, "/13").token(19));
        verify(outbound).apply(any());

        // when
        promise.complete(CoapResponse.of(null));
        assertFalse(resp.isDone());

        // and
        SeparateResponse cpResp = CoapResponse.ok("ok").toSeparate(Opaque.variableUInt(19), LOCAL_5683);
        assertTrue(exchangeFilter.handleResponse(cpResp));

        // then
        assertEquals(cpResp.asResponse(), resp.join());
        assertEquals(0, exchangeFilter.transactions());
    }

    @Test
    void shouldHandleSeparateResponse_whenEmptyAckComesLater() {
        // given
        resp = service.apply(CoapRequest.get(LOCAL_5683, "/13").token(19));
        verify(outbound).apply(any());

        // when
        SeparateResponse cpResp = CoapResponse.ok("ok").toSeparate(Opaque.variableUInt(19), LOCAL_5683);
        assertTrue(exchangeFilter.handleResponse(cpResp));

        // then
        assertEquals(CoapResponse.ok("ok"), resp.join());
        assertEquals(0, exchangeFilter.transactions());
        assertTrue(promise.isCancelled());
    }

    @Test
    void nonConfirmableExchange() {
        // given
        CoapRequest req = CoapRequest.get(LOCAL_5683, "/13").token(19).context(TransportContext.NON_CONFIRMABLE);
        resp = service.apply(req);
        assertEquals(1, exchangeFilter.transactions());

        // when
        SeparateResponse cpResp = CoapResponse.ok("ok").toSeparate(Opaque.variableUInt(19), LOCAL_5683);
        assertTrue(exchangeFilter.handleResponse(cpResp));

        // then
        assertEquals(CoapResponse.ok("ok"), resp.join());
        verify(outbound).apply(req);
        assertEquals(0, exchangeFilter.transactions());
        assertTrue(promise.isCancelled());
    }

    @Test
    void failExchange() {
        // given
        CoapRequest req = CoapRequest.get(LOCAL_5683, "/13");
        resp = service.apply(req);

        // when
        promise.completeExceptionally(new IOException());

        // then
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(IOException.class);
        assertEquals(0, exchangeFilter.transactions());
    }

    @Test
    void cancelledPromise() {
        // given
        CoapRequest req = CoapRequest.get(LOCAL_5683, "/13");
        resp = service.apply(req);
        assertEquals(1, exchangeFilter.transactions());

        // when
        resp.cancel(false);

        // then
        assertTrue(promise.isCancelled());
        assertEquals(0, exchangeFilter.transactions());
    }
}
