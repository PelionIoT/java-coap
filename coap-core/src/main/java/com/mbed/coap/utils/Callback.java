/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.utils;

/**
 * @author szymon
 */
public interface Callback<T> {

    /**
     * Callback instance that will ignore all calls
     */
    Callback IGNORE = new Callback() {
        @Override
        public void callException(Exception ex) {
            //ignore
        }

        @Override
        public void call(Object o) {
            //ignore
        }
    };

    /**
     * Returns callback instance that will ignore all calls
     *
     * @param <T> generic type
     * @return Callback instance
     */
    @SuppressWarnings("unchecked")
    static <T> Callback<T> ignore() {
        return IGNORE;
    }

    void call(T t);

    void callException(Exception ex);
}
