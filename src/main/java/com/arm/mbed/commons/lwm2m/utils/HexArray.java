/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.utils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Utility class to convert byte array to string.
 *
 * @author szymon
 */
public final class HexArray implements Serializable {

    private final static String HEX_DIGIT_STRING = "0123456789abcdef";
    private final static char[] HEX_DIGITS = HEX_DIGIT_STRING.toCharArray();
    private final byte[] data;

    /**
     * Converts byte array to hex string.
     *
     * @param data byte array data
     * @return hex string
     */
    public static String toHex(final byte[] data) {
        return data != null ? toHex(data, data.length) : null;
    }

    /**
     * Converts byte array to hex string.
     *
     * @param data byte array data
     * @param len byte array length
     * @return hex string
     */
    public static String toHex(final byte[] data, final int len) {
        final char[] retVal = new char[len * 2];
        int k = 0;
        for (int i = 0; i < len; i++) {
            retVal[k++] = HEX_DIGITS[(data[i] & 0xf0) >>> 4];
            retVal[k++] = HEX_DIGITS[data[i] & 0x0f];
        }
        return new String(retVal);
    }

    /**
     * Converts byte array to hex string.
     *
     * @param data byte array data
     * @param maxLen maximum number of bytes to be used
     * @return hex string
     */
    public static String toHexShort(final byte[] data, final int maxLen) {
        if (data.length <= maxLen) {
            return toHex(data, data.length);
        } else {
            return toHex(data, maxLen) + "..";
        }
    }

    /**
     * Converts hex string to byte array.
     *
     * @param hex hex string
     * @return byte array
     */
    public static byte[] fromHex(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i=0; i<hex.length(); i+=2) {
            b[i/2] = (byte) (HEX_DIGIT_STRING.indexOf(hex.charAt(i))*16 + HEX_DIGIT_STRING.indexOf(hex.charAt(i+1)) );
        }

        return b;
    }

    public HexArray(byte... data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return toHex(data, data.length);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HexArray other = (HexArray) obj;
        if (!Arrays.equals(this.data, other.data)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + Arrays.hashCode(this.data);
        return hash;
    }

}
