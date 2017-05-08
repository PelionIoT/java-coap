/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.transport;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java8.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    Helper class for running blocking operation for transport reading.
 */
public class TransportExecutors {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportExecutors.class);
    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(0);

    public static Executor newWorker(String name) {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, name + "-" + POOL_NUMBER.incrementAndGet()));
    }

    public static void loop(Executor executor, Supplier<Boolean> task) {
        Runnable command = new Runnable() {
            @Override
            public void run() {
                if (task.get()) {
                    try {
                        executor.execute(this);
                    } catch (RejectedExecutionException e) {
                        //can be ignored
                    } catch (Exception e) {
                        LOGGER.error(e.toString());
                    }
                }
            }
        };

        executor.execute(command);
    }

    public static void shutdown(Executor executor) {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdownNow();
        }
    }
}
