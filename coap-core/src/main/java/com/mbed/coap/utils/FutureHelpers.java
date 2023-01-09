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
package com.mbed.coap.utils;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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
            logger.error(ex.getMessage(), ex);
            return null;
        };
    }

    public static Function<Throwable, Void> logErrorIgnoreCancelled(Logger logger) {
        return ex -> {
            if (ex.getCause() instanceof CancellationException) {
                logger.debug(ex.getMessage());
            } else {
                logger.error(ex.getMessage(), ex);
            }
            return null;
        };
    }

    public static <T> void become(CompletableFuture<T> promise, CompletionStage<T> future) {
        future.whenComplete((v, err) -> {
            if (err != null) {
                promise.completeExceptionally(err);
            } else {
                promise.complete(v);
            }
        });
    }

    public static <T> T wrapExceptions(WrapSupplier<T> f) {
        try {
            return f.invoke();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @FunctionalInterface
    public interface WrapSupplier<T> {
        @SuppressWarnings({"PMD.SignatureDeclareThrowsException"})
        T invoke() throws Exception;
    }
}
