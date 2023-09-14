/**
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
package com.mbed.coap.server;

import java.net.InetSocketAddress;
import junit.framework.TestCase;

public class CoapRequestIdTest extends TestCase  {

    CoapRequestId requestId = new CoapRequestId(5000, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId2 = new CoapRequestId(5000, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId3 = new CoapRequestId(5002, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId4 = new CoapRequestId(5000, new InetSocketAddress("192.168.0.1", 20000));

    public void testGetCreatedTimestampMillis() {
        assert(Math.abs(System.currentTimeMillis() - requestId.getCreatedTimestampMillis()) < 1000);
    }

    public void testGetMid() {
        assert(requestId.getMid() == 5000);
    }

    public void testGetSourceAddress() {
        assert(requestId.getSourceAddress().equals(new InetSocketAddress("127.0.0.1", 20000)));
    }

    public void testTestEquals() {
        assert(requestId.equals(requestId));
        assert(requestId.equals(requestId2));
        assert(!requestId.equals(requestId3));
        assert(!requestId.equals(requestId4));
    }


    public void testTestHashCode() {
        assert(requestId.hashCode() == requestId.hashCode());
        assert(requestId.hashCode() == requestId2.hashCode());
        assert(requestId.hashCode() != requestId3.hashCode());
        assert(requestId.hashCode() != requestId4.hashCode());
    }
}
