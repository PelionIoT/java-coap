/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

/**
 * @author szymon
 */
public class CoapMessageFormatException extends CoapException {

    public CoapMessageFormatException() {
        super("Malformed CoAP message");
    }

    public CoapMessageFormatException(String message) {
        super(message);
    }

    public CoapMessageFormatException(Throwable cause) {
        super(cause);
    }

}
