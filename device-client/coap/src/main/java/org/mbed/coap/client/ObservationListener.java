/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;

/**
 * @author szymon
 */
public interface ObservationListener {

    void onObservation(CoapPacket obsPacket) throws CoapException;

    void onTermination(CoapPacket obsPacket) throws CoapException;
}
