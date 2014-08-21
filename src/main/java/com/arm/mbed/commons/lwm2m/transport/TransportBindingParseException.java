/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.transport;

/**
 *
 * @author szymon
 */
public class TransportBindingParseException extends Exception {

    public TransportBindingParseException() {
        super("Malformed transport binding");
    }

}
