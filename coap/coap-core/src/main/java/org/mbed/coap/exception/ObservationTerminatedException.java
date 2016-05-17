/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.exception;

import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.transport.TransportContext;

/**
 * @author szymon
 */
public class ObservationTerminatedException extends CoapException {

    private final CoapPacket packet;
    private final TransportContext context;

    public ObservationTerminatedException(CoapPacket packet, TransportContext context) {
        super("Coap message exception");
        this.packet = packet;
        this.context = context;
    }

    public CoapPacket getNotification() {
        return packet;
    }

    public TransportContext getTransportContext() {
        return context;
    }
}
