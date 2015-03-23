/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

/**
 * @author szymon
 */
public interface Callback<T> {

    /**
     * Callback instance that will ignore all calls
     */
    @SuppressWarnings("PMD.UnusedModifier")
    static final Callback IGNORE = new Callback() {
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
     */
    @SuppressWarnings("unchecked")
    static <T> Callback<T> ignore() {
        return IGNORE;
    }

    void call(T t);

    void callException(Exception ex);
}
