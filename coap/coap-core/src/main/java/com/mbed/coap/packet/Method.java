/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapException;

/**
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
