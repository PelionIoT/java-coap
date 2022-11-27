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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TimerTest {

    private final Timer timer = Timer.toTimer(Executors.newSingleThreadScheduledExecutor());

    @Test
    void shouldSchedule() {
        Runnable task = Mockito.mock(Runnable.class);
        Runnable task2 = Mockito.mock(Runnable.class);

        timer.schedule(Duration.ofMillis(3), task);
        timer.schedule(Duration.ofMillis(1), task2);

        verify(task, timeout(100)).run();
        verify(task2, timeout(100)).run();
    }

    @Test
    void shouldCancel() throws InterruptedException {
        Runnable task = Mockito.mock(Runnable.class);

        Runnable cancel = timer.schedule(Duration.ofMillis(10), task);
        cancel.run();

        Thread.sleep(11);
        verify(task, never()).run();
    }
}
