/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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

import static org.junit.Assert.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

/**
 * @author szymon
 */
public class FutureCallbackAdapterTest {

    @Test
    public void test() throws InterruptedException, ExecutionException, TimeoutException {
        FutureCallbackAdapter<String> f = new FutureCallbackAdapter<>();
        assertFalse(f.isDone());
        assertFalse(f.isCancelled());

        f.call("test");

        assertEquals("test", f.get());
        assertEquals("test", f.get(1, TimeUnit.SECONDS));
        assertTrue(f.isDone());
        assertFalse(f.cancel(true));
        assertFalse(f.isCancelled());

    }

    @Test
    public void exceptionTest() throws Exception {
        FutureCallbackAdapter<String> f = new FutureCallbackAdapter<>();
        assertFalse(f.isDone());

        f.callException(new Exception("test"));
        assertTrue(f.isDone());

        try {
            f.get();
        } catch (ExecutionException ex) {
            assertEquals("test", ex.getCause().getMessage());
        }

        try {
            f.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            assertEquals("test", ex.getCause().getMessage());
        }
    }

    @Test(expected = java.util.concurrent.TimeoutException.class)
    public void timeoutTest() throws Exception {

        FutureCallbackAdapter<String> f = new FutureCallbackAdapter<>();
        f.get(10, TimeUnit.MILLISECONDS);
    }
}
