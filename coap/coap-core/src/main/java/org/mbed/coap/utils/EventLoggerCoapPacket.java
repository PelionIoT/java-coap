/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import org.mbed.coap.packet.CoapPacket;

/**
 * @author szymon
 */
public class EventLoggerCoapPacket {

    private final CoapPacket coapPacket;

    public EventLoggerCoapPacket(CoapPacket coapPacket) {
        this.coapPacket = coapPacket;
    }

    @Override
    public String toString() {
        return coapPacket.toString(false, true, false).replace('|', ',');
    }

}
