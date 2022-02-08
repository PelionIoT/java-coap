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
package com.mbed.coap.server.filter;

import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import org.junit.jupiter.api.Test;

class EtagGeneratorFilterTest {
    private final Filter.SimpleFilter<CoapRequest, CoapResponse> filter = new EtagGeneratorFilter(__ -> ofBytes(1, 2));

    @Test
    void shouldAddEtagToResponse() {
        Service<CoapRequest, CoapResponse> service = filter.then(req -> completedFuture(ok("ok")));

        assertEquals(ofBytes(1, 2), service.apply(CoapRequest.get("/test")).join().options().getEtag());
    }

    @Test
    void shouldNotChangeEtagToResponseWhenExists() {
        Service<CoapRequest, CoapResponse> service = filter.then(req -> completedFuture(ok("ok").etag(ofBytes(99))));

        assertEquals(ofBytes(99), service.apply(CoapRequest.get("/test")).join().options().getEtag());
    }
}