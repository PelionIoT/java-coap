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
package com.mbed.coap.observe;

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.of;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservationHandlerTest {

    private final ObservationHandler obs = new ObservationHandler();
    private final Service<CoapRequest, CoapResponse> service = mock(Service.class);


    @BeforeEach
    void setUp() {
        reset(service);
    }

    @Test
    void missingObservationRelation() {
        assertFalse(obs.hasObservation(of("100")));
        obs.terminate(of("100"), notFound());

        assertFalse(obs.notify(null, of("100"), ok("OK"), service));
    }

    @Test
    void shouldTerminate() {
        // given
        CompletableFuture<CoapResponse> promise = obs.nextSupplier(of("100"), "/obs").get();
        assertTrue(obs.hasObservation(of("100")));

        // when
        obs.terminate(of("100"), notFound());

        // then
        assertFalse(obs.hasObservation(of("100")));
        assertEquals(notFound(), promise.join());
    }

    @Test
    void shouldNotify() {
        // given
        Supplier<CompletableFuture<CoapResponse>> supplier = obs.nextSupplier(of("100"), "/obs");
        CompletableFuture<CoapResponse> promise = supplier.get();
        promise.thenRun(supplier::get);
        assertTrue(obs.hasObservation(of("100")));

        // when
        assertTrue(obs.notify(null, of("100"), ok("21C"), service));

        // then
        assertEquals(ok("21C"), promise.join());
    }

    @Test
    void shouldNotifyAndRetrieveBlocks() {
        // given
        Supplier<CompletableFuture<CoapResponse>> supplier = obs.nextSupplier(of("100"), "/obs");
        CompletableFuture<CoapResponse> promise = supplier.get();
        promise.thenRun(supplier::get);
        given(service.apply(get("/obs").block2Res(1, S_16, false))).willReturn(completedFuture(ok("bbb")));

        // when
        assertTrue(obs.notify(null, of("100"), ok("aaaaaaaaaaaaaaaa").block2Res(0, S_16, true), service));

        // then
        assertEquals(ok("aaaaaaaaaaaaaaaabbb"), promise.join());
    }

    @Test
    void shouldFailWhenUnexpectedBlockRetrieving() {
        // given
        Supplier<CompletableFuture<CoapResponse>> supplier = obs.nextSupplier(of("100"), "/obs");
        CompletableFuture<CoapResponse> promise = supplier.get();
        promise.thenRun(supplier::get);
        given(service.apply(get("/obs").block2Res(1, S_16, false))).willReturn(completedFuture(notFound()));

        // when
        assertTrue(obs.notify(null, of("100"), ok("aaaaaaaaaaaaaaaa").block2Res(0, S_16, true), service));

        // then
        assertFalse(promise.isDone());
    }

    @Test
    void shouldFailWhenMalformedBlockSize() {
        // given
        Supplier<CompletableFuture<CoapResponse>> supplier = obs.nextSupplier(of("100"), "/obs");
        CompletableFuture<CoapResponse> promise = supplier.get();
        promise.thenRun(supplier::get);

        // when
        assertFalse(obs.notify(null, of("100"), ok("aaa").block2Res(0, S_16, true), service));

        // then
        assertThrows(CancellationException.class, promise::join);
    }

    @Test
    void shouldCancelPreviousPromiseIfNotCompleted() {
        // given
        Supplier<CompletableFuture<CoapResponse>> supplier = obs.nextSupplier(of("100"), "/obs");
        CompletableFuture<CoapResponse> promise = supplier.get();

        // when
        assertFalse(supplier.get().isDone());

        // then
        assertTrue(promise.isCancelled());
    }

}