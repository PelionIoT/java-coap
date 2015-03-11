/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap;

import org.mbed.coap.utils.CoapCallback;

/**
 * @author szymon
 */
public class CoapUtils {

    private static CoapCallback nullCallback;

    public static String decodeString(byte[] byteString) {
        return new String(byteString, CoapConstants.DEFAULT_CHARSET);
    }

    public static byte[] encodeString(String payload) {
        if (payload == null || payload.length() == 0) {
            return new byte[0];
        }
        return payload.getBytes(CoapConstants.DEFAULT_CHARSET);
    }

    /**
     * Returns callback instance that will ignore all calls
     *
     * @return callback instance
     */
    public synchronized static CoapCallback getCallbackNull() {
        if (nullCallback == null) {
            nullCallback = new CoapCallback() {

                @Override
                public void callException(Exception ex) {
                    // nothing to do
                }

                @Override
                public void call(CoapPacket t) {
                    // nothing to do
                }
            };
        }

        return nullCallback;
    }
}
