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

import com.mbed.coap.observe.ObservationHandler;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

class ObserveRequestFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private final AtomicLong nextToken = new AtomicLong(0);
    private final ObservationHandler observationHandler;
    private final static Integer INIT_OBSERVE = 0;

    ObserveRequestFilter(ObservationHandler observationHandler) {
        this.observationHandler = observationHandler;
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest req, Service<CoapRequest, CoapResponse> service) {
        if (!INIT_OBSERVE.equals(req.options().getObserve())) {
            return service.apply(req);
        }

        CoapRequest obsReq;
        if (req.getToken().isEmpty()) {
            obsReq = req.token(Opaque.variableUInt(nextToken.incrementAndGet()));
        } else {
            obsReq = req;
        }

        return service.apply(obsReq)
                .thenApply(resp -> {
                            if (resp.options().getObserve() != null) {
                                return resp.nextSupplier(observationHandler.nextSupplier(obsReq.getToken(), obsReq.options().getUriPath()));
                            } else {
                                return resp;
                            }
                        }
                );
    }
}
