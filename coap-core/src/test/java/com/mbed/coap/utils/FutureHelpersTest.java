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
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class FutureHelpersTest {

    @Test
    void shouldBecome() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> future = completedFuture("dupa");

        FutureHelpers.become(promise, future);

        assertEquals("dupa", promise.join());
    }

    @Test
    void shouldBecome_exception() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> future = failedFuture(new IOException());

        FutureHelpers.become(promise, future);

        assertTrue(promise.isCompletedExceptionally());
        assertThatThrownBy(promise::join).hasCauseExactlyInstanceOf(IOException.class);
    }
}
