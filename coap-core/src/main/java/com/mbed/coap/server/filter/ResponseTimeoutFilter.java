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

import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.utils.Filter.SimpleFilter;
import com.mbed.coap.utils.Service;
import com.mbed.coap.utils.Timer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ResponseTimeoutFilter<REQ, RES> implements SimpleFilter<REQ, RES> {

    private final Timer timer;
    private final Function<REQ, Duration> timeoutResolver;

    public ResponseTimeoutFilter(Timer timer, Function<REQ, Duration> timeoutResolver) {
        this.timer = timer;
        this.timeoutResolver = timeoutResolver;
    }

    @Override
    public CompletableFuture<RES> apply(REQ request, Service<REQ, RES> service) {
        CompletableFuture<RES> promise = service.apply(request);

        Runnable cancel = timer.schedule(timeoutResolver.apply(request), () ->
                promise.completeExceptionally(new CoapTimeoutException())
        );

        promise.whenComplete((__, err) -> cancel.run());
        return promise;
    }
}
