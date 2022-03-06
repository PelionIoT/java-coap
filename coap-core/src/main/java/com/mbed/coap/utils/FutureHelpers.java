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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;

public class FutureHelpers {

    // Remove when migrated to java 11+
    public static <T> CompletableFuture<T> failedFuture(Exception ex) {
        Objects.requireNonNull(ex);
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    public static Function<Throwable, Void> log(Logger logger) {
        return ex -> {
            logger.warn(ex.getMessage());
            return null;
        };
    }

    public static Function<Throwable, Void> logError(Logger logger) {
        return ex -> {
            logger.error(ex.getMessage());
            return null;
        };
    }

}
