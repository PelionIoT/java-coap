/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport.udp;

import com.mbed.coap.transport.TransportContext;

/**
 * @author szymon
 */
public class TrafficClassTransportContext {

    public static final int DEFAULT = 0;
    public static final int HIGH = 127;
    public static final int HIGHEST = 255;

    static final String TRAFFIC_CLASS = "TrafficClass";

    public static TransportContext create(Integer trafficClass, TransportContext tc) {
        return tc.add(TRAFFIC_CLASS, trafficClass);
    }

    static Integer readFrom(TransportContext tc) {
        return tc.getAndCast(TRAFFIC_CLASS, Integer.class);
    }
}
