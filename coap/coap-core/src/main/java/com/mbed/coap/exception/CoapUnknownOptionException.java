/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

import com.mbed.coap.packet.Code;

/**
 * @author szymon
 */
public class CoapUnknownOptionException extends CoapCodeException {

    public CoapUnknownOptionException(int type) {
        super(Code.C402_BAD_OPTION, "Unknown option header: " + type);
    }
}
