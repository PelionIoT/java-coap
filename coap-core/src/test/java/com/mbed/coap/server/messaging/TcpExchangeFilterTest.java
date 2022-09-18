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
import static com.mbed.coap.packet.CoapResponse.of;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.transport.TransportContext.EMPTY;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class TcpExchangeFilterTest {
    private TcpExchangeFilter exchange = new TcpExchangeFilter();
    private final Service<CoapRequest, Boolean> sender = __ -> completedFuture(true);

    private CompletableFuture<CoapResponse> resp;
    private CompletableFuture<CoapResponse> resp2;

    @Test
    void successfulSingleRequest() {
        // given
        resp = exchange.apply(get(LOCAL_5683, "/13"), sender);
        assertEquals(1, exchange.transactions());

        // when
        assertTrue(exchange.handleResponse(new SeparateResponse(ok("ok"), Opaque.EMPTY, LOCAL_5683, EMPTY)));

        // then
        assertEquals(ok("ok"), resp.join());
        assertEquals(0, exchange.transactions());
    }

    @Test
    void successfulMultipleRequests() {
        // given
        CoapRequest req = get(LOCAL_5683, "/13").token(1001);
        CoapRequest req2 = get(LOCAL_5683, "/14").token(2002);
        resp = exchange.apply(req, sender);
        resp2 = exchange.apply(req2, sender);
        assertEquals(2, exchange.transactions());

        // when (response in different order)
        assertTrue(exchange.handleResponse(new SeparateResponse(ok("ok"), variableUInt(2002), LOCAL_5683, EMPTY)));

        // then
        assertEquals(ok("ok"), resp2.join());
        assertEquals(1, exchange.transactions());

        // and
        assertTrue(exchange.handleResponse(new SeparateResponse(ok("ok2"), variableUInt(1001), LOCAL_5683, EMPTY)));
        assertTrue(resp.isDone());
        assertEquals(0, exchange.transactions());
    }

    @Test
    void failWhenSendingFails() {
        // when
        resp = exchange.apply(get(LOCAL_5683, "/13"), __ -> failedFuture(new IOException()));

        // then
        assertEquals(0, exchange.transactions());
        assertTrue(resp.isCompletedExceptionally());
    }

    @Test
    void cancelBeforeGettingResponse() {
        // given
        resp = exchange.apply(get(LOCAL_5683, "/13"), sender);

        // when
        resp.cancel(false);

        // then
        assertEquals(0, exchange.transactions());
        assertFalse(exchange.handleResponse(new SeparateResponse(ok("ok"), Opaque.EMPTY, LOCAL_5683, EMPTY)));
    }

    @Test
    void failWhenAborted() {
        // given
        resp = exchange.apply(get(LOCAL_5683, "/13"), sender);
        resp2 = exchange.apply(get(LOCAL_5683, "/14").token(123), sender);
        CompletableFuture<CoapResponse> resp3 = exchange.apply(get(LOCAL_1_5683, "/16"), sender);

        // when
        assertTrue(exchange.handleResponse(of(Code.C705_ABORT).toSeparate(Opaque.EMPTY, LOCAL_5683)));

        // then
        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
        assertFalse(resp3.isDone());
        assertEquals(1, exchange.transactions());

    }

    @Test
    public void should_ignore_non_matching_response() {
        assertFalse(exchange.handleResponse(ok("ok").toSeparate(Opaque.EMPTY, LOCAL_5683)));
    }

    @Test
    public void should_replay_pong_to_ping() throws Exception {
        // given
        resp = exchange.apply(CoapRequest.ping(LOCAL_5683, EMPTY), sender);
        assertEquals(1, exchange.transactions());

        // when
        assertTrue(exchange.handleResponse(new SeparateResponse(CoapResponse.of(Code.C703_PONG, Opaque.EMPTY), Opaque.EMPTY, LOCAL_5683, EMPTY)));

        // then
        assertEquals(CoapResponse.of(Code.C703_PONG, Opaque.EMPTY), resp.join());
        assertEquals(0, exchange.transactions());
    }

}