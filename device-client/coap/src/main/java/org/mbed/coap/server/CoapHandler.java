/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import org.mbed.coap.exception.CoapException;

/**
 * Interface for handling CoAO requests.
 *
 * @author szymon
 */
public interface CoapHandler {

    void handle(CoapExchange exchange) throws CoapException;
}
