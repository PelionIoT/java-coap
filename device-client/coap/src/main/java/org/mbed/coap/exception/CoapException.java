/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

/**
 *
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
