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
package com.mbed.coap.utils;

import static org.junit.Assert.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.Test;

/**
 * @author szymon
 */
public class IpPortAddressTest {

    @Test
    public void test() throws UnknownHostException {
        IpPortAddress addr = new IpPortAddress(new InetSocketAddress("127.0.0.1", 5683));

        assertArrayEquals(addr.getIp(), new byte[]{127, 0, 0, 1});
        assertEquals(addr.getPort(), 5683);
        assertEquals(addr, new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683));
        assertEquals(addr.hashCode(), new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683).hashCode());

        assertEquals(addr, new IpPortAddress(new InetSocketAddress(InetAddress.getByAddress("perse", new byte[]{127, 0, 0, 1}), 5683)));

        assertFalse(addr.equals(null));
        assertFalse(addr.equals("dsa"));
        assertFalse(addr.equals(new IpPortAddress(new byte[]{127, 0, 0, 46}, 5683)));
        assertFalse(addr.equals(new IpPortAddress(new byte[]{127, 0, 0, 1}, 1)));

        assertEquals(new InetSocketAddress("127.0.0.1", 5683), new IpPortAddress(new byte[]{127, 0, 0, 1}, 5683).toInetSocketAddress());
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void wrongIp() {
        new IpPortAddress(new byte[8], 65);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void wrongPort() {
        new IpPortAddress(new byte[4], 545456);
    }

    @Test
    public void toStringTest() {
        //v6
        IpPortAddress addr = new IpPortAddress(new InetSocketAddress("[2001::1:0:1234]", 4321));
        assertEquals("[2001::1:0:1234]:4321", addr.toString());
        assertEquals("[2001::1:0:1234]:4321", new IpPortAddress(new InetSocketAddress("[2001:0000:0000:0000:0000:0001:0000:1234]", 4321)).toString());

        //v4
        addr = new IpPortAddress(new InetSocketAddress("127.0.0.1", 61616));
        assertEquals("127.0.0.1:61616", addr.toString());
    }
}
