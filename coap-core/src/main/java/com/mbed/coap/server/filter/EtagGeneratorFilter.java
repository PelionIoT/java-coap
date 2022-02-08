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

import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class EtagGeneratorFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {

    private final Function<Opaque, Opaque> etagGenerator;

    public EtagGeneratorFilter(Function<Opaque, Opaque> etagGenerator) {
        this.etagGenerator = Objects.requireNonNull(etagGenerator);
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        return service
                .apply(request)
                .thenApply(resp -> {
                    if (resp.options().getEtagArray() == null) {
                        return resp.etag(etagGenerator.apply(resp.getPayload()));
                    }
                    return resp;
                });
    }
}
