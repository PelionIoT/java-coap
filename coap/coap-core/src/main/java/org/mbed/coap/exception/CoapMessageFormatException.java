/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

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
