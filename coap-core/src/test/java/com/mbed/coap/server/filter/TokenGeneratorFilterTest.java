/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapResponse.ok;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class TokenGeneratorFilterTest {
    private final Filter.SimpleFilter<CoapRequest, CoapResponse> filter = TokenGeneratorFilter.RANDOM;

    @Test
    void shouldSetTokenToRequest() {
        Service<CoapRequest, CoapResponse> service = filter.then(req -> {
            assertTrue(req.getToken().nonEmpty());
            System.out.println(req);
            return completedFuture(ok("ok"));
        });

        assertEquals(ok("ok"), service.apply(get("/test")).join());
    }

    @Test
    void shouldNotSetTokenToWhenAlreadyExists() {
        Service<CoapRequest, CoapResponse> service = filter.then(req -> {
            assertEquals(Opaque.ofBytes(0x7b), req.getToken());
            return completedFuture(ok("ok"));
        });

        assertEquals(ok("ok"), service.apply(get("/test").token(123)).join());
    }

    @Test
    void sequentialTokenGenerator() {
        Supplier<Opaque> gen = TokenGeneratorFilter.sequential(12).tokenGenerator;

        assertEquals("0d", gen.get().toHex());
        assertEquals("0e", gen.get().toHex());
        assertEquals("0f", gen.get().toHex());
    }

    @Test
    void sequentialTokenGenerator_flip() {
        Supplier<Opaque> gen = TokenGeneratorFilter.sequential(Long.MAX_VALUE - 1).tokenGenerator;

        assertEquals("7fffffffffffffff", gen.get().toHex());
        assertEquals("00", gen.get().toHex());
        assertEquals("01", gen.get().toHex());
    }
}
