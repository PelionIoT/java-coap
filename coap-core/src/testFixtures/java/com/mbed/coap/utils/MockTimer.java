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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MockTimer implements Timer {

    private final List<Runnable> tasks = new ArrayList<>();
    private Duration lastScheduledDelay = null;

    @Override
    public Runnable schedule(Duration delay, Runnable task) {
        lastScheduledDelay = delay;
        tasks.add(task);
        return () -> tasks.removeIf(entry -> task == entry);
    }

    public void runAll() {
        List<Runnable> tmp = new ArrayList<>(tasks);
        tasks.clear();
        tmp.forEach(Runnable::run);
    }

    public int size() {
        return tasks.size();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    public Duration getLastScheduledDelay() {
        return lastScheduledDelay;
    }
}
