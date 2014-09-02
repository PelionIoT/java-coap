/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mbed.coap.test.utils;

/**
 *
 * @author szymon
 */
public class Utils {

    public static byte[] intToByteArray(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value};
    }
}
