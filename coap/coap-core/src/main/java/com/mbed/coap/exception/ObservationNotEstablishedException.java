/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.exception;

import com.mbed.coap.packet.CoapPacket;

/**
 * @author szymon
 */
public class ObservationNotEstablishedException extends CoapException {

    private transient CoapPacket response;

    public ObservationNotEstablishedException() {
        super("Observation can not be established");
    }

    public ObservationNotEstablishedException(CoapPacket resp) {
        this();
        this.response = resp;
    }

    public CoapPacket getResponse() {
        return response;
    }

}
