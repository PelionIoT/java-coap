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

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

public class AsyncQueue<T> {

    private final LinkedList<CompletableFuture<T>> queue = new LinkedList<>();

    public synchronized void add(T obj) {
        if (!queue.isEmpty() && !queue.getFirst().isDone()) {
            queue.removeFirst().complete(obj);
        } else {
            queue.addLast(completedFuture(obj));
        }
    }

    public synchronized void addException(Exception ex) {
        if (!queue.isEmpty() && !queue.getFirst().isDone()) {
            queue.removeFirst().completeExceptionally(ex);
        } else {
            queue.addLast(failedFuture(ex));
        }
    }

    public synchronized CompletableFuture<T> poll() {
        if (!queue.isEmpty() && queue.getFirst().isDone()) {
            return queue.removeFirst();
        }

        CompletableFuture<T> promise = new CompletableFuture<>();
        queue.addLast(promise);
        return promise;
    }

    public synchronized void removeAll() {
        while (!queue.isEmpty()) {
            queue.removeFirst().cancel(false);
        }
    }
}
