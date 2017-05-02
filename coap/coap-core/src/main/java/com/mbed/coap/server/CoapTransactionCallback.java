/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
public interface CoapTransactionCallback extends Callback<CoapPacket> {

    void messageResent();

    void blockReceived();
}
