/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

import org.mbed.coap.Code;

/**
 *
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

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
    }

}
