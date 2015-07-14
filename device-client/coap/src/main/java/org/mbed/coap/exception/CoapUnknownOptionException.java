/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

import org.mbed.coap.packet.Code;

/**
 * @author szymon
 */
public class CoapUnknownOptionException extends CoapCodeException {

    public CoapUnknownOptionException(int type) {
        super(Code.C402_BAD_OPTION, "Unknown option header: " + type);
    }
}
