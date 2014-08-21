/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import org.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
public interface ObservationHandler extends Callback<CoapExchange> {

    /**
     * Returns true observation relation is established.
     *
     * @param token observation token
     */
    boolean hasObservation(byte[] token);

}
