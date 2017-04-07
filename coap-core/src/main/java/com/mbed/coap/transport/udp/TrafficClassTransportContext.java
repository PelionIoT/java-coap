/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
