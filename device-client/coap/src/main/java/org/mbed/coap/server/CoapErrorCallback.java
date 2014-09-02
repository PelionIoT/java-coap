/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package org.mbed.coap.server;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.exception.CoapException;

/**
 * Callback interface for Coap Server error cases.
 * 
 * @author nordav01
 */
public interface CoapErrorCallback {
    
    void parserError(byte[] packet, CoapException exception);

    void duplicated(CoapPacket request);

}
