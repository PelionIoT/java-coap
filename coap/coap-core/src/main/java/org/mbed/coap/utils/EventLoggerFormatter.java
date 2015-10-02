/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package org.mbed.coap.utils;

import java.net.InetSocketAddress;

/**
 * @author nordav01
 */
public class EventLoggerFormatter {

    private static final String SEP = " | ";

    public String format (String category, InetSocketAddress address, Object details) {
        StringBuffer sbuf = new StringBuffer(256);
        sbuf.append(category).append(SEP);

        if (address != null) {
            sbuf.append(address.getHostString()).append(':').append(address.getPort()).append(SEP);
        } else {
            sbuf.append('-').append(SEP);
        }

        String det = details.toString().replace("\r\n", "??");
        sbuf.append('\"').append(det).append('\"');

        return sbuf.toString();
    }
}
