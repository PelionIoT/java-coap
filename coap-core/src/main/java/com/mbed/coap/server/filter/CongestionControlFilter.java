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

import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class CongestionControlFilter<DEST, REQ, RES> implements Filter.SimpleFilter<REQ, RES> {
    private final int maxWaitingRequests;
    private final Function<REQ, DEST> destinationFunc;
    private final ConcurrentMap<DEST, SequentialTaskRunner<RES>> interactions = new ConcurrentHashMap<>();

    public CongestionControlFilter(int maxWaitingRequests, Function<REQ, DEST> destinationFunc) {
        this.maxWaitingRequests = maxWaitingRequests;
        this.destinationFunc = destinationFunc;
    }

    @Override
    public CompletableFuture<RES> apply(REQ request, Service<REQ, RES> service) {
        DEST dest = destinationFunc.apply(request);

        CompletableFuture<RES> respFuture = add(dest, () -> service.apply(request));

        respFuture.thenRun(() -> removeIfEmpty(dest));
        return respFuture;

    }

    private void removeIfEmpty(DEST dest) {
        interactions.computeIfPresent(dest, (__, tasks) -> {
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks;
        });
    }

    private CompletableFuture<RES> add(DEST dest, Supplier<CompletableFuture<RES>> task) {
        AtomicReference<CompletableFuture<RES>> respFuture = new AtomicReference<>();
        interactions.compute(dest, (__, tasks) -> {
            if (tasks == null) {
                tasks = new SequentialTaskRunner<>(maxWaitingRequests);
            }
            respFuture.set(tasks.add(task));
            return tasks;
        });
        return respFuture.get();
    }
}
