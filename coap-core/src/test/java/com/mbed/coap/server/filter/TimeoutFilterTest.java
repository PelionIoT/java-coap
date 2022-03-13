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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.utils.MockTimer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class TimeoutFilterTest {

    private MockTimer timer = new MockTimer();
    private TimeoutFilter<String, String> filter = new TimeoutFilter<>(timer, Duration.ofMinutes(2));

    @Test
    void shouldTimeoutRequest() {
        final CompletableFuture<String> servicePromise = new CompletableFuture<>();
        CompletableFuture<String> resp = filter.apply("req", s -> servicePromise);

        // when, timeout hits
        timer.runAll();

        // then
        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
        assertTrue(servicePromise.isCompletedExceptionally());
    }

    @Test
    void shouldForwardResult() {
        final CompletableFuture<String> servicePromise = CompletableFuture.completedFuture("OK");

        // when
        CompletableFuture<String> resp = filter.apply("req", s -> servicePromise);

        // then
        assertTrue(resp.isDone());
        assertEquals("OK", resp.join());
        timer.isEmpty();
    }
}
