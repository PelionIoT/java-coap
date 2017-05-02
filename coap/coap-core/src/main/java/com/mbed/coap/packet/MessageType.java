/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapException;

/**
 * @author szymon
 */
public enum MessageType {

    Confirmable, NonConfirmable, Acknowledgement, Reset;

    public static MessageType valueOf(int transactionMessage) throws CoapException {
        switch (transactionMessage) {
            case 0:
                return Confirmable;
            case 1:
                return NonConfirmable;
            case 2:
                return Acknowledgement;
            case 3:
                return Reset;
            default:
                throw new CoapException("Wrong transaction message code");
        }
    }

    @Override
    public String toString() {
        return super.toString().substring(0, 3).toUpperCase();
    }

}
