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
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;

public class EtagValidatorFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        return service
                .apply(request)
                .thenApply(resp -> {
                    if (request.getMethod() == Method.GET) {
                        return validateEtag(request, resp);
                    } else {
                        return resp;
                    }
                });
    }

    private CoapResponse validateEtag(CoapRequest request, CoapResponse resp) {
        if (request.options().getEtagArray() != null && resp.options().getEtag() != null) {
            for (Opaque etag : request.options().getEtagArray()) {
                if (etag.equals(resp.options().getEtag())) {
                    return new CoapResponse(Code.C203_VALID, Opaque.EMPTY, resp.options());
                }
            }
        }
        return resp;
    }
}
