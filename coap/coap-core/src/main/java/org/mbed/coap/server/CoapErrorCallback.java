/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */

package org.mbed.coap.server;

import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;

/**
 * Callback interface for Coap Server error cases.
 *
 * @author nordav01
 */
public interface CoapErrorCallback {
    CoapErrorCallback NULL = new CoapErrorCallback() {
        @Override
        public void parserError(byte[] packet, CoapException exception) {
            //ignore
        }

        @Override
        public void duplicated(CoapPacket request) {
            //ignore
        }
    };

    void parserError(byte[] packet, CoapException exception);

    void duplicated(CoapPacket request);

}
