/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.model;

interface ResourceValidator {
    
    boolean isValid (byte[] value);
    
    boolean isValid (int value);
    
    boolean isValid (String value);

}
