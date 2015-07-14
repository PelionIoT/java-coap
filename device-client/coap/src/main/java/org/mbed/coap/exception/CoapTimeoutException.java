/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

/**
 * @author szymon
 */
public class CoapTimeoutException extends CoapException {

    public CoapTimeoutException() {
        super("Timeout");
    }
}
