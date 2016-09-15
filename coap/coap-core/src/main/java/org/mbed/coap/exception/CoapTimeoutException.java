/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

import org.mbed.coap.server.internal.CoapTransaction;

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
