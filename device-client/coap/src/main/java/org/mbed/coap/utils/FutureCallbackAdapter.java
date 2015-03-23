/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import java.util.concurrent.CompletableFuture;

/**
 * @author szymon
 */
public class FutureCallbackAdapter<V> extends CompletableFuture<V> implements Callback<V> {

    @Override
    public void call(V value) {
        this.complete(value);
    }

    @Override
    public void callException(Exception ex) {
        this.completeExceptionally(ex);
    }

}
