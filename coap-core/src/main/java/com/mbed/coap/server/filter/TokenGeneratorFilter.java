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

import static java.util.Objects.requireNonNull;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class TokenGeneratorFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {

    final Supplier<Opaque> tokenGenerator;

    private static final Random random = new Random();
    public static final TokenGeneratorFilter RANDOM = new TokenGeneratorFilter(() ->
            Opaque.variableUInt(random.nextLong())
    );

    public static TokenGeneratorFilter sequential(long startToken) {
        final AtomicLong current = new AtomicLong(startToken);

        return new TokenGeneratorFilter(() -> Opaque.variableUInt(current.incrementAndGet()));
    }

    public TokenGeneratorFilter(Supplier<Opaque> tokenGenerator) {
        this.tokenGenerator = requireNonNull(tokenGenerator);
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        if (!request.isPing() && request.getToken().isEmpty()) {
            return service.apply(request.token(tokenGenerator.get()));
        }

        return service.apply(request);
    }
}
