/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */

package com.mbed.coap.server;

import com.mbed.coap.packet.CoapPacket;

public interface DuplicatedCoapMessageCallback {
    DuplicatedCoapMessageCallback NULL = request -> {
        //ignore
    };

    void duplicated(CoapPacket request);

}
