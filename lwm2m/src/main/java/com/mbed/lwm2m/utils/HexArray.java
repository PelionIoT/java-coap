/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.lwm2m.utils;

/**
 * Utility class to convert byte array to string.
 *
 * @author szymon
 */
public final class HexArray {

    private final static String HEX_DIGIT_STRING = "0123456789abcdef";
    private final static char[] HEX_DIGITS = HEX_DIGIT_STRING.toCharArray();

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
     * Converts hex string to byte array.
     *
     * @param hex hex string
     * @return byte array
     */
    public static byte[] fromHex(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            b[i / 2] = (byte) (HEX_DIGIT_STRING.indexOf(hex.charAt(i)) * 16 + HEX_DIGIT_STRING.indexOf(hex.charAt(i + 1)));
        }

        return b;
    }

}
