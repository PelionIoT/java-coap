/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */

package com.mbed.coap.utils;

/**
 * Simple implementation for {@link Callback} interface that stores the latest value only.
 * @author nordav01
 */
public class SimpleCallbackAdapter<V> implements Callback<V> {

    private V value;
    private Exception exception;

    @Override
    public void call(V value) {
        this.value = value;
    }

    @Override
    public void callException(Exception exception) {
        this.exception = exception;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public V get() throws Exception {
        if (exception != null) {
            throw exception;
        }
        return value;
    }

}
