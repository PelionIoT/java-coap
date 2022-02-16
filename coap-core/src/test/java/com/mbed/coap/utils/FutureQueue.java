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
package com.mbed.coap.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FutureQueue<T> implements Supplier<CompletableFuture<T>> {
    public volatile CompletableFuture<T> promise = null;

    @Override
    public CompletableFuture<T> get() {
        if (promise != null) {
            throw new IllegalStateException();
        }
        promise = new CompletableFuture<>();
        return promise;
    }

    public boolean cancel() {
        if (promise != null) {
            CompletableFuture<T> tmpPromise = this.promise;
            this.promise = null;
            return tmpPromise.cancel(false);
        }
        return false;
    }

    public boolean put(T obj) {
        if (promise != null) {
            CompletableFuture<T> tmpPromise = this.promise;
            this.promise = null;
            return tmpPromise.complete(obj);
        }
        return false;
    }
}
