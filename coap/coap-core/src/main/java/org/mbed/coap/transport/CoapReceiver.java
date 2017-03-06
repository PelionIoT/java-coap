/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import org.mbed.coap.packet.CoapPacket;

public interface CoapReceiver {

    void handle(CoapPacket packet, TransportContext transportContext);
}
