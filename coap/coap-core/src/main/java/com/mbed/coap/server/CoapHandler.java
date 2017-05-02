/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapException;

/**
 * Interface for handling CoAP requests.
 *
 * @author szymon
 */
public interface CoapHandler {

    void handle(CoapExchange exchange) throws CoapException;
}
