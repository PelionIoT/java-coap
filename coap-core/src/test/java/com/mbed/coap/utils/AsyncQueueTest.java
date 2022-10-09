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

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsyncQueueTest {

    private final AsyncQueue<String> queue = new AsyncQueue<>();

    @BeforeEach
    void setUp() {
        queue.removeAll();
    }

    @Test
    public void addFirst() {
        queue.add("1");
        queue.add("2");
        queue.add("3");

        assertEquals("1", queue.poll().join());
        assertEquals("2", queue.poll().join());
        assertEquals("3", queue.poll().join());
        assertFalse(queue.poll().isDone());
    }

    @Test
    public void pollFirst() {
        CompletableFuture<String> resp1 = queue.poll();
        CompletableFuture<String> resp2 = queue.poll();
        CompletableFuture<String> resp3 = queue.poll();

        queue.add("1");
        queue.add("2");
        queue.add("3");

        assertEquals("1", resp1.join());
        assertEquals("2", resp2.join());
        assertEquals("3", resp3.join());
    }
}