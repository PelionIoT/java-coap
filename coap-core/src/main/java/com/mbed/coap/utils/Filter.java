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
package com.mbed.coap.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/*
Filter is a transformer of a 'service' that may intercept and transform inputs and outputs
 */
@FunctionalInterface
public interface Filter<REQ, RES, IN_REQ, IN_RES> extends BiFunction<REQ, Service<IN_REQ, IN_RES>, CompletableFuture<RES>> {

    CompletableFuture<RES> apply(REQ request, Service<IN_REQ, IN_RES> service);

    default <REQ2, RES2> Filter<REQ, RES, REQ2, RES2> andThen(Filter<IN_REQ, IN_RES, REQ2, RES2> next) {
        return (request, service) -> {
            Service<IN_REQ, IN_RES> nextService = request2 -> next.apply(request2, service);
            return apply(request, nextService);
        };
    }

    default Service<REQ, RES> then(Service<IN_REQ, IN_RES> function) {
        return request -> apply(request, function);
    }


    interface SimpleFilter<REQ, RES> extends Filter<REQ, RES, REQ, RES> {

    }

    interface UnaryFilter<T> extends Filter<T, T, T, T> {

    }

    static <REQ, RES> SimpleFilter<REQ, RES> identity() {
        return new SimpleFilter<REQ, RES>() {
            @Override
            public CompletableFuture<RES> apply(REQ request, Service<REQ, RES> service) {
                return service.apply(request);
            }

            @Override
            public <REQ2, RES2> Filter<REQ, RES, REQ2, RES2> andThen(Filter<REQ, RES, REQ2, RES2> next) {
                return next;
            }

            @Override
            public Service<REQ, RES> then(Service<REQ, RES> service) {
                return service;
            }
        };
    }
}
