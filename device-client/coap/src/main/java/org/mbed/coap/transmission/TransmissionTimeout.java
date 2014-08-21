/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transmission;

/**
 * @author szymon
 */
public interface TransmissionTimeout {

    /**
     * Calculates timeout for given sending attempt counter. Note that first
     * attempt starts with value: 1
     *
     * @param attemptCounter attempt counter
     * @return timeout in milliseconds
     */
    long getTimeout(int attemptCounter);

    long getMulticastTimeout(int attempt);

}
