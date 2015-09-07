/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

import org.mbed.coap.packet.CoapPacket;

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
