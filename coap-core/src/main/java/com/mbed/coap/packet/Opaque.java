/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
package com.mbed.coap.packet;

import com.mbed.coap.CoapConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Opaque {
    private final static String HEX_DIGIT_STRING = "0123456789abcdef";
    private final static char[] HEX_DIGITS = HEX_DIGIT_STRING.toCharArray();
    private final byte[] data;
    public static final Opaque EMPTY = new Opaque(new byte[0]);

    public Opaque(byte[] data) {
        this.data = data;
    }

    public static Opaque of(byte... data) {
        return new Opaque(data);
    }

    public static Opaque ofBytes(int... unsignedBytes) {
        byte[] data = new byte[unsignedBytes.length];
        for (int i = 0; i < unsignedBytes.length; i++) {
            if (unsignedBytes[i] < 0 || unsignedBytes[i] > 255) {
                throw new IllegalArgumentException("Not a byte: " + unsignedBytes[i]);
            }
            data[i] = ((byte) unsignedBytes[i]);
        }
        return new Opaque(data);
    }

    public static Opaque of(String text) {
        return new Opaque(text.getBytes(StandardCharsets.UTF_8));
    }

    public static Opaque variableUInt(long num) {
        int len = 8;

        if (num <= 0xffL) {
            len = 1;
        } else if (num <= 0xffffL) {
            len = 2;
        } else if (num <= 0xffffffL) {
            len = 3;
        } else if (num <= 0xffffffffL) {
            len = 4;
        } else if (num <= 0xffffffffffL) {
            len = 5;
        } else if (num <= 0xffffffffffffL) {
            len = 6;
        } else if (num <= 0xffffffffffffffL) {
            len = 7;
        }

        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) (0xFF & (num >> 8 * (len - (i + 1))));
        }
        return new Opaque(data);
    }

    public static Opaque decodeHex(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            b[i / 2] = (byte) (hexIndex(hex.charAt(i)) * 16 + hexIndex(hex.charAt(i + 1)));
        }
        return new Opaque(b);
    }

    private static int hexIndex(char ch) {
        int index = HEX_DIGIT_STRING.indexOf(ch);
        if (index < 0) {
            throw new IllegalArgumentException("Illegal hex character");
        }
        return index;
    }

    public static Opaque read(InputStream inputStream, int len) throws IOException {
        byte[] data = new byte[len];
        inputStream.read(data);
        return new Opaque(data);
    }

    public int size() {
        return data.length;
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(data);
    }

    public void writeTo(OutputStream outputStream, int startPos, int len) throws IOException {
        outputStream.write(data, startPos, len);
    }

    @Override
    public String toString() {
        return toHex();
    }

    public String toHex() {
        return toHex(data.length);
    }

    public String toHex(int maxLen) {
        return toHexShort(maxLen);
    }

    String toHexShort(final int maxLen) {
        if (data.length <= maxLen) {
            return encodeToHex(data.length);
        } else {
            return encodeToHex(maxLen) + "..";
        }
    }

    private String encodeToHex(final int len) {
        final char[] retVal = new char[len * 2];
        int k = 0;
        for (int i = 0; i < len; i++) {
            retVal[k++] = HEX_DIGITS[(data[i] & 0xf0) >>> 4];
            retVal[k++] = HEX_DIGITS[data[i] & 0x0f];
        }
        return new String(retVal);
    }

    public long toLong() {
        if (data.length > 8) {
            throw new IllegalArgumentException();
        }
        long val = 0L;
        for (byte b : data) {
            val <<= 8;
            val += (b & 0xFF);
        }
        return val;
    }

    public int toInt() {
        if (data.length > 4) {
            throw new IllegalArgumentException();
        }
        return ((int) toLong());
    }

    public String toUtf8String() {
        return new String(data, CoapConstants.DEFAULT_CHARSET);
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    public boolean nonEmpty() {
        return data.length > 0;
    }

    public Opaque concat(Opaque other) {
        byte[] newArr = new byte[data.length + other.data.length];
        System.arraycopy(data, 0, newArr, 0, data.length);
        System.arraycopy(other.data, 0, newArr, data.length, other.data.length);
        return new Opaque(newArr);
    }

    public Opaque slice(int indexFrom, int len) {
        return new Opaque(
                Arrays.copyOfRange(data, indexFrom, indexFrom + len)
        );
    }

    public byte[] getBytes() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Opaque opaque = (Opaque) o;
        return Arrays.equals(data, opaque.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

}
