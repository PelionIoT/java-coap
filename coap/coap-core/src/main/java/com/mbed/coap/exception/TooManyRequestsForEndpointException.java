/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

/**
 * Created by olesmi01 on 29.8.2016.
 */
public class TooManyRequestsForEndpointException extends CoapException {
    public TooManyRequestsForEndpointException(Throwable cause) {
        super(cause);
    }

    public TooManyRequestsForEndpointException(String message) {
        super(message);
    }

    public TooManyRequestsForEndpointException(String message, Throwable cause) {
        super(message, cause);
    }

    public TooManyRequestsForEndpointException(String format, Object... args) {
        super(format, args);
    }
}
