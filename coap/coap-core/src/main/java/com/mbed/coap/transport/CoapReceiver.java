/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import com.mbed.coap.packet.CoapPacket;

public interface CoapReceiver {

    void handle(CoapPacket packet, TransportContext transportContext);
}
