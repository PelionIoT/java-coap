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

import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SequentialTaskRunnerTest {
    private SequentialTaskRunner<String> queue = new SequentialTaskRunner<>(3);

    @Test
    void shouldRunTasksInSequence() {
        CompletableFuture<String> promise1 = new CompletableFuture<>();

        // given
        CompletableFuture<String> resp = queue.add(() -> promise1);
        CompletableFuture<String> resp2 = queue.add(() -> completedFuture("ok2"));
        CompletableFuture<String> resp3 = queue.add(() -> completedFuture("ok3"));

        assertFalse(resp.isDone());
        assertFalse(resp2.isDone());
        assertFalse(resp3.isDone());
        assertFalse(queue.isEmpty());

        // when
        promise1.complete("ok1");

        // then
        assertEquals("ok1", resp.getNow(null));
        assertEquals("ok2", resp2.getNow(null));
        assertEquals("ok3", resp3.getNow(null));
        assertTrue(queue.isEmpty());
    }

    @Test
    void shouldFailToAddWhenTooMany() {
        queue = new SequentialTaskRunner<>(2);

        // when
        CompletableFuture<String> resp = queue.add(CompletableFuture::new);
        CompletableFuture<String> resp2 = queue.add(CompletableFuture::new);
        CompletableFuture<String> resp3 = queue.add(CompletableFuture::new);

        // then
        assertFalse(resp.isDone());
        assertFalse(resp2.isDone());
        assertTrue(resp3.isCompletedExceptionally());
    }
}