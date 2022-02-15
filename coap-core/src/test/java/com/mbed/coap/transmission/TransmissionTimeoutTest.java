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
package com.mbed.coap.transmission;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class TransmissionTimeoutTest {

    @Test
    public void coapTimeoutTest() {
        //1 second base timeout
        CoapTimeout coapTimeout = new CoapTimeout(1000);

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.getTimeout(1) >= 1000);
            assertTrue(coapTimeout.getTimeout(1) <= 1500);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.getTimeout(2) >= 2000);
            assertTrue(coapTimeout.getTimeout(2) <= 3000);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.getTimeout(3) >= 4000);
            assertTrue(coapTimeout.getTimeout(3) <= 6000);
        }

        //5 seconds base timeout
        coapTimeout = new CoapTimeout(5000);
        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.getTimeout(1) >= 5000);
            assertTrue(coapTimeout.getTimeout(1) <= 7500);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.getTimeout(2) >= 10000);
            assertTrue(coapTimeout.getTimeout(2) <= 75000);
        }
        assertTrue(coapTimeout.getTimeout(1246) < 0);
        assertTrue(coapTimeout.getMulticastTimeout(1246) < 0);
        assertTrue(coapTimeout.getMulticastTimeout(1) == CoapTimeout.MULTICAST_TIMEOUT);

        try {
            coapTimeout.getTimeout(-1);
            fail("Exception expected");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void coapTimeoutCounter() {
        CoapTimeout coapTimeout = new CoapTimeout();
        assertTrue(coapTimeout.getTimeout(1) > 0);
        assertTrue(coapTimeout.getTimeout(2) > 0);
        assertTrue(coapTimeout.getTimeout(3) > 0);
        assertTrue(coapTimeout.getTimeout(4) > 0);
        assertTrue(coapTimeout.getTimeout(5) > 0);
        //no more timeouts
        assertTrue(coapTimeout.getTimeout(6) < 0);
        assertTrue(coapTimeout.getTimeout(7) < 0);
        assertTrue(coapTimeout.getTimeout(8) < 0);
    }

    @Test
    public void coapTimeoutCounter_customRetransmissionAttempts() {
        CoapTimeout coapTimeout = new CoapTimeout(2000, 2);
        assertTrue(coapTimeout.getTimeout(1) > 0);
        assertTrue(coapTimeout.getTimeout(2) > 0);
        assertTrue(coapTimeout.getTimeout(3) > 0);
        assertTrue(coapTimeout.getTimeout(4) < 0);
        assertTrue(coapTimeout.getTimeout(5) < 0);

        coapTimeout = new CoapTimeout(2000, 0);
        assertTrue(coapTimeout.getTimeout(1) > 0);
        assertTrue(coapTimeout.getTimeout(2) < 0);
        assertTrue(coapTimeout.getTimeout(3) < 0);

        assertEquals(new CoapTimeout(2000, 4), new CoapTimeout());
        assertEquals(new CoapTimeout(2000, 4).hashCode(), new CoapTimeout().hashCode());
    }

    @Test()
    public void singleTimeoutTest() {
        final int TIMEOUT = 10000;
        final int MULT_TIMEOUT = 20000;

        SingleTimeout singleTimeout = new SingleTimeout(TIMEOUT, MULT_TIMEOUT);

        assertEquals(TIMEOUT, singleTimeout.getTimeout(1));
        assertEquals(-1, singleTimeout.getTimeout(2));
        assertEquals(-1, singleTimeout.getTimeout(3));
        assertEquals(-1, singleTimeout.getTimeout(4));
        assertEquals(-1, singleTimeout.getTimeout(5));
        assertEquals(-1, singleTimeout.getTimeout(20));

        try {
            singleTimeout.getTimeout(0);
            fail("Exception expected");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            singleTimeout.getTimeout(-1);
            fail("Exception expected");
        } catch (IllegalArgumentException ex) {
            //expected
        }

        assertEquals(-1, singleTimeout.getMulticastTimeout(-1));
        assertEquals(-1, singleTimeout.getMulticastTimeout(0));
        assertEquals(MULT_TIMEOUT, singleTimeout.getMulticastTimeout(1));
        assertEquals(-1, singleTimeout.getMulticastTimeout(2));
        assertEquals(-1, singleTimeout.getMulticastTimeout(3));
        assertEquals(-1, singleTimeout.getMulticastTimeout(10));

    }
}
