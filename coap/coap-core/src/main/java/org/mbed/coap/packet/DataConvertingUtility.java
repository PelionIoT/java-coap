/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.mbed.coap.CoapConstants;

/**
 * Utility class that provides static helper methods for creating and parsing CoAP packet
 *
 * @author szymon
 */
public final class DataConvertingUtility {

    private DataConvertingUtility() {
        //keep private
    }

    static byte[][] stringArrayToBytes(String[] strArray) {
        byte[][] bt;
        int skip = 0;
        if (strArray.length > 0 && strArray[0].length() == 0) {
            bt = new byte[strArray.length - 1][];
            skip = 1;
        } else {
            bt = new byte[strArray.length][];
        }
        for (int i = 0; i < bt.length; i++) {
            bt[i] = encodeString(strArray[i + skip]);
        }
        return bt;
    }

    /**
     * Converts given byte array to unsigned long number.
     *
     * @param data byte array with variable length
     * @return converted number
     */
    public static Long readVariableULong(byte[] data) {
        if (data == null) {
            return null;
        }
        Long val = 0L;
        for (byte b : data) {
            val <<= 8;
            val += (b & 0xFF);
        }
        return val;
    }

    static byte[][] writeVariableUInt(short[] value) {
        List<byte[]> btList = new ArrayList<>();

        for (short aValue : value) {
            btList.add(writeVariableUInt(aValue, 1));
        }
        return btList.toArray(new byte[btList.size()][1]);
    }

    /**
     * Splits string with given character. Unlike String.split(..) this method
     * does not remove empty elements.
     *
     * @param val text to be split
     * @param ch splitting character
     */
    static String[] split(String val, char ch) {
        int offset = 0;
        ArrayList<String> list = new ArrayList<>();
        int nextPos = val.indexOf(ch, offset);

        while (nextPos != -1) {
            list.add(val.substring(offset, nextPos));
            offset = nextPos + 1;
            nextPos = val.indexOf(ch, offset);
        }
        if (offset == 0) {
            return new String[]{val};
        }

        list.add(val.substring(offset, val.length()));
        return list.toArray(new String[list.size()]);
    }

    public static Map<String, String> parseUriQuery(String uriQuery) throws ParseException {
        if (uriQuery == null || uriQuery.length() == 0) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] params = uriQuery.substring(uriQuery.indexOf('?') + 1).split("&");

        for (String prm : params) {
            String[] p = prm.split("=", 2);
            if (p.length != 2) {
                throw new ParseException("", 0);
            }
            result.put(p[0], p[1]);
        }
        return result;
    }

    public static Map<String, List<String>> parseUriQueryMult(String uriQuery) throws ParseException {
        //TODO: parse for multiple values
        if (uriQuery == null || uriQuery.length() == 0) {
            return new HashMap<>(); //empty map
            //return null;
        }
        Map<String, List<String>> result = new HashMap<>();
        String[] params = uriQuery.substring(uriQuery.indexOf('?') + 1).split("&");

        for (String prm : params) {
            String[] p = prm.split("=", 2);
            if (p.length != 2) {
                throw new ParseException("", 0);
            }
            List<String> values = new LinkedList<>();
            values.add(p[1]);
            result.put(p[0], values);
        }
        return result;
    }

    /**
     * Converts given number into byte array.
     *
     * @param value number to convert
     * @return converted byte array
     */
    public static byte[] convertVariableUInt(Long value) {
        if (value == null) {
            return null;
        }
        return writeVariableUInt(value, 1);
    }

    public static byte[] convertVariableUInt(long value) {
        return writeVariableUInt(value, 1);
    }

    static byte[] writeVariableUInt(long value, int minBytes) {
        int len = 1;
        if (value > 0) {
            len = (int) Math.ceil((Math.log10(value + 1) / Math.log10(2)) / 8); //calculates needed minimum length
        }
        len = Math.max(len, minBytes);
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) (0xFF & (value >> 8 * (len - (i + 1))));
        }
        return data;
    }

    static byte[][] extendOption(byte[][] orig, byte[] extend) {
        if (orig == null || orig.length == 0) {
            return new byte[][]{extend};
        } else {
            byte[][] arr = new byte[orig.length + 1][];
            System.arraycopy(orig, 0, arr, 0, orig.length);
            arr[orig.length] = extend;
            return arr;
        }
    }

    static short[] extendOption(short[] orig, byte[] extend) {
        if (orig == null || orig.length == 0) {
            return new short[]{readVariableULong(extend).shortValue()};
        } else {
            short[] arr = new short[orig.length + 1];
            System.arraycopy(orig, 0, arr, 0, orig.length);
            arr[orig.length] = readVariableULong(extend).shortValue();
            return arr;
        }
    }

    static String extendOption(String orig, byte[] extend, String delimiter, boolean startWithDelimiter) {
        String extOption = orig;
        if (extOption == null) {
            extOption = "";
        }
        if (extOption.length() == 0 && !startWithDelimiter) {
            return extOption + decodeToString(extend);
        } else {
            return extOption + delimiter + decodeToString(extend);
        }
    }

    public static String decodeToString(byte[] byteString) {
        return new String(byteString, CoapConstants.DEFAULT_CHARSET);
    }

    public static byte[] encodeString(String payload) {
        if (payload == null || payload.length() == 0) {
            return new byte[0];
        }
        return payload.getBytes(CoapConstants.DEFAULT_CHARSET);
    }

    public static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static byte[] combine(byte[] arr1, byte[] arr2) {
        byte[] newArr = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, newArr, 0, arr1.length);
        System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
        return newArr;
    }
}
