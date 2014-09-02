/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

/**
 *
 * @author szymon
 */
public interface Callback<T> extends CallbackEx<T, Exception> {

    @Override
    void callException(Exception ex);
}
