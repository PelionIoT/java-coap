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

import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class SequentialTaskRunner<T> {
    private final int max;
    private final AtomicReference<CompletableFuture<T>> last = new AtomicReference<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public SequentialTaskRunner(int max) {
        this.max = max;
    }

    public CompletableFuture<T> add(Supplier<CompletableFuture<T>> task) {
        if (counter.incrementAndGet() > max) {
            counter.decrementAndGet();
            return failedFuture(new TooManyRequestsForEndpointException(""));
        }
        CompletableFuture<T> promise = new CompletableFuture<>();

        CompletableFuture<T> prev = last.getAndSet(promise);
        if (prev == null) {
            become(promise, task.get());
        } else {
            prev.whenComplete((__, err) ->
                    become(promise, task.get())
            );
        }

        promise.thenRun(counter::decrementAndGet)
                .thenRun(() -> last.compareAndSet(promise, null));

        return promise;
    }

    boolean isEmpty() {
        return last.get() == null;
    }
}
