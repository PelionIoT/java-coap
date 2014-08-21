/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

/**
 *
 * @author szymon
 */
public interface CallbackEx<T, E extends Exception> {

    void call(T t);

    void callException(E ex);
}
