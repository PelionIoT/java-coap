/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

/**
 * @author szymon
 */
public class CoapException extends Exception {

    public CoapException(Throwable cause) {
        super(cause);
    }

    public CoapException(String message) {
        super(message);
    }

    public CoapException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoapException(String format, Object... args) {
        super(String.format(format, args));
    }
}
