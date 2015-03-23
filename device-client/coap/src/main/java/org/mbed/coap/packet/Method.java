/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

import org.mbed.coap.exception.CoapException;

/**
 *
 * @author szymon
 */
public enum Method {

    GET, POST, PUT, DELETE;

    public static Method valueOf(int methodCode) throws CoapException {
        switch (methodCode) {
            case 1:
                return GET;
            case 2:
                return POST;
            case 3:
                return PUT;
            case 4:
                return DELETE;
            default:
                throw new CoapException("Wrong method code");
        }
    }

    public int getCode() {
        return this.ordinal() + 1;
    }
}
