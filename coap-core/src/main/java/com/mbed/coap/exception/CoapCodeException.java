/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

import com.mbed.coap.packet.Code;

/**
 * @author szymon
 */
public class CoapCodeException extends CoapException {

    private final Code code;

    public CoapCodeException(Code code) {
        super(code.toString().substring(1).replace("_", " "));
        this.code = code;
    }

    public CoapCodeException(Code code, Throwable throwable) {
        super(code.toString().substring(1).replace("_", " "), throwable);
        this.code = code;
    }

    public CoapCodeException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CoapCodeException(Code code, String message, Throwable throwable) {
        super(code.toString().substring(1).replace("_", " ") + " " + message, throwable);
        this.code = code;
    }

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
    }

}
