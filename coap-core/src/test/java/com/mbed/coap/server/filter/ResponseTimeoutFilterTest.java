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

import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.utils.MockTimer;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ResponseTimeoutFilterTest {

    private MockTimer timer = new MockTimer();
    private ResponseTimeoutFilter<String, String> filter = new ResponseTimeoutFilter<>(timer, req -> {
        if (req.startsWith("req-timeout-")) {
            return ofMinutes(Integer.parseInt(req.substring(12)));
        }
        return ofMinutes(2);
    });

    @Test
    void shouldScheduleResponseTimeout() {
        final CompletableFuture<String> servicePromise = new CompletableFuture<>();
        CompletableFuture<String> resp = filter.apply("req", s -> servicePromise);

        // when, timeout hits
        assertEquals(ofMinutes(2), timer.getLastScheduledDelay());
        timer.runAll();

        // then
        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::join).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
        assertTrue(servicePromise.isCompletedExceptionally());
    }

    @Test
    void shouldScheduleResponseTimeoutWithCustomDelay() {
        final CompletableFuture<String> servicePromise = new CompletableFuture<>();
        CompletableFuture<String> resp = filter.apply("req-timeout-13", s -> servicePromise);

        // when, timeout hits
        assertEquals(ofMinutes(13), timer.getLastScheduledDelay());
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
