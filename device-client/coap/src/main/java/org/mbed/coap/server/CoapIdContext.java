/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

/**
 *
 * @author user
 */
public interface CoapIdContext {

    /**
     * Gets next unique coap message identifier, limited to values from 0 to
     * 0xFFFF
     *
     * @return next unique message id
     */
    int getNextMID();

}
