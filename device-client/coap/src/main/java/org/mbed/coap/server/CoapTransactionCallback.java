/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.utils.Callback;

/**
 *
 * @author szymon
 */
public interface CoapTransactionCallback extends Callback<CoapPacket> {

    void messageResent();

    void blockReceived();
}
