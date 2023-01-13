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
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RescueFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RescueFilter.class);

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        try {
            return service.apply(request).exceptionally(this::rescue);
        } catch (Exception ex) {
            return CompletableFuture.completedFuture(rescue(ex));
        }
    }

    private CoapResponse rescue(Throwable ex) {
        if (ex instanceof CompletionException) {
            return rescue(ex.getCause());
        }
        if (ex instanceof CoapCodeException) {
            return ((CoapCodeException) ex).toResponse();
        }

        LOGGER.error("Unexpected exception: {}", ex.getMessage(), ex);
        return CoapResponse.of(Code.C500_INTERNAL_SERVER_ERROR);
    }

}
