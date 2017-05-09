/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.mbed.lwm2m.model;

/**
 * @author nordav01
 *
 */
class DefaultResourceValidator implements ResourceValidator {

    private final boolean verdict;
    
    public DefaultResourceValidator (boolean verdict) {
        this.verdict = verdict;
    }
    
    @Override
    public boolean isValid(String value) {
        return verdict;
    }
    
    @Override
    public boolean isValid(int value) {
        return verdict;
    }

    @Override
    public boolean isValid(byte[] value) {
        return verdict;
    }

}
