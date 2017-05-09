/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.mbed.lwm2m.tlv;

public class TLV {

    /** HTTP Media type for OMA TLV content. */
    public static final String CT_APPLICATION_LWM2M_TLV = "application/vnd.oma.lwm2m+tlv";

    static final byte TYPE_RESOURCE = (byte) 0b11_000000;
    static final byte TYPE_MULTIPLE_RESOURCE = (byte) 0b10_000000;
    static final byte TYPE_RESOURCE_INSTANCE = (byte) 0b01_000000;
    static final byte TYPE_OBJECT_INSTANCE = (byte) 0b00_000000;

    static final int ID8 = 0b00_000000;
    static final int ID16 = 0b00_1_00000;

    static final int LENGTH8 = 0b000_01_000;
    static final int LENGTH16 = 0b000_10_000;
    static final int LENGTH24 = 0b000_11_000;

    private TLV() {
    }
}
