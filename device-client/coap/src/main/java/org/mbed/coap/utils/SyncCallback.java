/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

/**
 *
 * @author szymon
 */
public class SyncCallback<T> implements Callback<T> {

    private T response;
    private Exception exception;
    private boolean hasResponse;

    public synchronized T getResponse() throws Exception, InterruptedException { //NOPMD
        while (!hasResponse) {
            this.wait();
        }
        if (exception != null) {
            throw exception;
        }
        return response;
    }

    public synchronized T getAndClear() throws Exception {  //NOPMD
        try {
            return getResponse();
        } finally {
            clear();
        }
    }

    public synchronized void clear() {
        this.response = null;
        exception = null;
        hasResponse = false;
    }

    @Override
    public synchronized void call(T response) {
        //System.out.println("Received: " + response.toString());
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
