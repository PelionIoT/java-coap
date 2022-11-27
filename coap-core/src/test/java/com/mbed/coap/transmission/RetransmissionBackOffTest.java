/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;


public class RetransmissionBackOffTest {

    @Test
    public void coapTimeoutTest() {
        //1 second base timeout
        RetransmissionBackOff coapTimeout = RetransmissionBackOff.ofExponential(ofSeconds(1), 4);

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.next(1).toMillis() >= 1000);
            assertTrue(coapTimeout.next(1).toMillis() <= 1500);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.next(2).toMillis() >= 2000);
            assertTrue(coapTimeout.next(2).toMillis() <= 3000);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.next(3).toMillis() >= 4000);
            assertTrue(coapTimeout.next(3).toMillis() <= 6000);
        }

        //5 seconds base timeout
        coapTimeout = RetransmissionBackOff.ofExponential(ofSeconds(5), 4);
        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.next(1).toMillis() >= 5000);
            assertTrue(coapTimeout.next(1).toMillis() <= 7500);
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(coapTimeout.next(2).toMillis() >= 10000);
            assertTrue(coapTimeout.next(2).toMillis() <= 75000);
        }
        assertTrue(coapTimeout.next(1246) == ZERO);

        try {
            coapTimeout.next(-1);
            fail("Exception expected");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void coapTimeoutCounter() {
        RetransmissionBackOff coapTimeout = RetransmissionBackOff.ofDefault();
        assertTrue(coapTimeout.next(1).toMillis() > 0);
        assertTrue(coapTimeout.next(2).toMillis() > 0);
        assertTrue(coapTimeout.next(3).toMillis() > 0);
        assertTrue(coapTimeout.next(4).toMillis() > 0);
        assertTrue(coapTimeout.next(5).toMillis() > 0);
        //no more timeouts
        assertEquals(ZERO, coapTimeout.next(6));
        assertEquals(ZERO, coapTimeout.next(7));
        assertEquals(ZERO, coapTimeout.next(8));
    }

    @Test
    public void coapTimeoutCounter_customRetransmissionAttempts() {
        RetransmissionBackOff coapTimeout = RetransmissionBackOff.ofExponential(ofSeconds(2), 2);
        assertTrue(coapTimeout.next(1).toMillis() > 0);
        assertTrue(coapTimeout.next(2).toMillis() > 0);
        assertTrue(coapTimeout.next(3).toMillis() > 0);
        assertEquals(ZERO, coapTimeout.next(4));
        assertEquals(ZERO, coapTimeout.next(5));

        coapTimeout = RetransmissionBackOff.ofExponential(ofSeconds(2), 0);
        assertTrue(coapTimeout.next(1).toMillis() > 0);
        assertEquals(ZERO, coapTimeout.next(2));
        assertEquals(ZERO, coapTimeout.next(3));
    }

    @Test()
    public void singleTimeoutTest() {
        RetransmissionBackOff singleTimeout = RetransmissionBackOff.ofFixed(ofSeconds(10));

        assertEquals(ofSeconds(10), singleTimeout.next(1));
        assertEquals(ZERO, singleTimeout.next(2));
        assertEquals(ZERO, singleTimeout.next(3));
        assertEquals(ZERO, singleTimeout.next(4));
        assertEquals(ZERO, singleTimeout.next(5));
        assertEquals(ZERO, singleTimeout.next(20));

        assertThrows(IllegalArgumentException.class, () -> singleTimeout.next(0));
        assertThrows(IllegalArgumentException.class, () -> singleTimeout.next(-1));
    }
}
