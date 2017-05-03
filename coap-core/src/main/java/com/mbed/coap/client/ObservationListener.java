/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.client;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;

/**
 * @author szymon
 */
public interface ObservationListener {

    void onObservation(CoapPacket obsPacket) throws CoapException;

    void onTermination(CoapPacket obsPacket) throws CoapException;
}
