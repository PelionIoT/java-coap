/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.lwm2m;

import static org.junit.Assert.*;
import java.util.Objects;
import org.junit.Test;

/**
 * @author nordav01
 */
public class LWM2MIDTest {

    @Test
    public void testEquals() {
        assertFalse(new LWM2MID(null).equals(null));
        assertTrue(Objects.equals(new LWM2MID(null), new LWM2MID(null)));
        assertTrue(Objects.equals(new LWM2MID(1), new LWM2MID(1)));
        assertTrue(Objects.equals(new LWM2MID("alma"), new LWM2MID("alma")));
        assertFalse(Objects.equals(new LWM2MID("alma"), new LWM2MID("korte")));
    }
    
    @Test
    public void testCompareTo() throws Exception {
        assertEquals(0, new LWM2MID(null).compareTo(new LWM2MID(null)));
        assertEquals(-1, new LWM2MID(null).compareTo(new LWM2MID("alma")));
        assertEquals(1, new LWM2MID("alma").compareTo(new LWM2MID(null)));
        assertEquals(-10, new LWM2MID("alma").compareTo(new LWM2MID("korte")));
        assertEquals(10, new LWM2MID("korte").compareTo(new LWM2MID("alma")));
    }
    
    @Test(expected=NullPointerException.class)
    public void testCompareToNull() throws Exception {
        new LWM2MID(null).compareTo(null);
    }
    
    @Test
    public void testHashCode() throws Exception {
        assertEquals (Objects.hashCode(-1), new LWM2MID(null).hashCode());
        assertEquals (Objects.hashCode(42), new LWM2MID("42").hashCode());
        assertEquals (Objects.hashCode(new LWM2MID("alma").intValue()), new LWM2MID("alma").hashCode());
    }

}
