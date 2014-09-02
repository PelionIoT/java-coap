/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author szymon
 */
public class FutureCallbackAdapter<V> implements Future<V>, Callback<V> {

    private V response;
    private Exception exception;
    private boolean hasResponse;

    @Override
    public boolean cancel(boolean bln) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public synchronized boolean isDone() {
        return hasResponse;
    }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (!hasResponse) {
            this.wait();
        }
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        return response;
    }

    @Override
    public synchronized V get(long l, TimeUnit tu) throws InterruptedException, ExecutionException, TimeoutException {
        if (!hasResponse) {
            this.wait(tu.toMillis(l));
            if (!hasResponse) {
                throw new TimeoutException();
            }
        }
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        return response;
    }

    @Override
    public synchronized void call(V response) {
        this.response = response;
        hasResponse = true;
        this.notifyAll();
    }

    @Override
    public synchronized void callException(Exception ex) {
        this.exception = ex;
        hasResponse = true;
        this.notifyAll();
    }
}
