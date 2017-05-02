/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

import com.mbed.coap.server.internal.CoapTransaction;

/**
 * @author szymon
 */
public class CoapTimeoutException extends CoapException {

    public CoapTimeoutException() {
        super("Timeout");
    }

    public CoapTimeoutException(CoapTransaction transaction) {
        super("Timeout " + transaction.toString());
    }
}
