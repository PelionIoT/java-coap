/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

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
