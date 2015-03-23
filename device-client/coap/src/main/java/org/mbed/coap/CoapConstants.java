/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap;

import java.nio.charset.Charset;

/**
 * CoAP constants that are defined in RFC 7252 document
 *
 * @author szymon
 */
public final class CoapConstants {

    public static final int DEFAULT_PORT = 5683;
    public static final String WELL_KNOWN_CORE = "/.well-known/core";
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    public static final long ACK_TIMEOUT = 2000;
    public static final float ACK_RANDOM_FACTOR = 1.5f;
    public static final Short MAX_RETRANSMIT = 4;

    private CoapConstants() {
    }

}
