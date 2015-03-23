package org.mbed.coap.utils;

import org.mbed.coap.packet.CoapPacket;

/**
 *
 * @author szymon
 */
public class EventLoggerCoapPacket {

    private final CoapPacket coapPacket;

    public EventLoggerCoapPacket(CoapPacket coapPacket) {
        this.coapPacket = coapPacket;
    }

    @Override
    public String toString() {
        return coapPacket.toString(true, true, false).replace('|', ',');
    }

}
