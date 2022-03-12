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
package com.mbed.coap.server;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ObserveRequestFilterTest {

    private ObserveRequestFilter filter = new ObserveRequestFilter(new ObservationHandler());
    private Service<CoapRequest, CoapResponse> service = filter.then(req -> completedFuture(ok(req.getToken())));

    @Test
    void shouldAddTokenForObservationRequest() {
        CompletableFuture<CoapResponse> resp = service.apply(observe(null, "/obs"));

        assertEquals(ok(ofBytes(1)), resp.join());
    }

    @Test
    void shouldNotChangeTokenForObservationRequestWithExistingToken() {
        CompletableFuture<CoapResponse> resp = service.apply(observe(null, "/obs").token(100));

        assertEquals(ok(ofBytes(100)), resp.join());
    }

    @Test
    void shouldNotChangeTokenForNonObservationRequest() {
        CompletableFuture<CoapResponse> resp = service.apply(get(null, "/obs"));

        assertEquals(ok(EMPTY), resp.join());
    }

    @Test
    void shouldSetNextSupplierForSuccessfulObservationResponse() {
        service = filter.then(req -> completedFuture(ok("ok").options(o -> o.setObserve(12))));

        CompletableFuture<CoapResponse> resp = service.apply(observe(null, "/obs"));

        assertNotNull(resp.join().next);
    }

    @Test
    void shouldNotSetNextSupplierForFailedObservationResponse() {
        service = filter.then(req -> completedFuture(ok("ok")));

        CompletableFuture<CoapResponse> resp = service.apply(observe(null, "/obs"));

        assertNull(resp.join().next);
    }
}