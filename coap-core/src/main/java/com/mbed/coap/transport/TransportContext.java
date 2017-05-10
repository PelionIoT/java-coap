/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import java.util.function.Supplier;

/**
 * This class provides transport context information.
 *
 * Created by szymon
 */
@FunctionalInterface
public interface TransportContext {

    TransportContext NULL = key -> null;

    Object get(Object key);

    default TransportContext add(Object key, Object val) {
        if (val == null) {
            return this;
        } else {
            return add(key, () -> val);
        }
    }

    default TransportContext add(Object key, Supplier func) {
        if (key == null) {
            throw new NullPointerException();
        }

        return k -> {
            if (key.equals(k)) {
                return func.get();
            } else {
                return this.get(k);
            }
        };
    }

    default <T> T getAndCast(Object key, Class<T> clazz) {
        Object val = get(key);
        if (val != null && clazz.isInstance(val)) {
            return ((T) val);
        } else {
            return null;
        }
    }

}
