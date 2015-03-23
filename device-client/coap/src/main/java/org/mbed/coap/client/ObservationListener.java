/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.exception.CoapException;

/**
 *
 * @author szymon
 */
public interface ObservationListener {

    void onObservation(CoapPacket obsPacket) throws CoapException;

    void onTermination(CoapPacket obsPacket) throws CoapException;
}
