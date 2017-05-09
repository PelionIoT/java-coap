/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

/**
 * Created by olesmi01 on 20.4.2016.
 * Too large entity received
 */
public class CoapBlockTooLargeEntityException extends CoapBlockException {
    public CoapBlockTooLargeEntityException(String message) {
        super(message);
    }
}
