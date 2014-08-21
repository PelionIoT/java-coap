/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transmission;

/**
 *
 * @author szymon
 */
public class SingleTimeout implements TransmissionTimeout {

    long timeout;
    long multicastTimeout = 2000;

    public SingleTimeout(long timeoutMili) {
        this.timeout = timeoutMili;
    }

    public SingleTimeout(long timeoutMili, long multicastTimeout) {
        this.timeout = timeoutMili;
        this.multicastTimeout = multicastTimeout;
    }

    @Override
    public long getTimeout(int attempt) {
        if (attempt > 1) {
            return -1;
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt can not be less than 0");
        }
        return timeout;
    }

    @Override
    public long getMulticastTimeout(int attempt) {
        if (attempt == 1) {
            return multicastTimeout;
        }
        return -1;
    }

}
