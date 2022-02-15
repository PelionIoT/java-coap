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
package com.mbed.lwm2m;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class LWM2MResourceInstanceTest {

    @Test
    public void createByString() {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), "instance");
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertArrayEquals("instance".getBytes(), instance.getValue());
    }

    @Test
    public void createByInteger() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), 42);
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertArrayEquals(new byte[]{42}, instance.getValue());
    }

    @Test
    public void createBy2ByteInteger() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), 554);
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertEquals("554", instance.getStringValue());
        assertArrayEquals(new byte[]{2, 42}, instance.getValue());
    }

    @Test
    public void createBy3ByteInteger() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), 65538);
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertEquals("65538", instance.getStringValue());
        assertArrayEquals(new byte[]{1, 0, 2}, instance.getValue());
    }

    @Test
    public void createBy4ByteInteger() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), 16777730);
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertEquals("16777730", instance.getStringValue());
        assertArrayEquals(new byte[]{1, 0, 2, 2}, instance.getValue());
    }

    @Test
    public void createByOpaque() throws Exception {
        byte[] opaque = "opaque".getBytes();
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), opaque);
        assertEquals(1, instance.getId().intValue());
        assertTrue(instance.hasValue());
        assertArrayEquals(opaque, instance.getValue());
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithNullID() throws Exception {
        assertThrows(NullPointerException.class, () ->
                new LWM2MResourceInstance(null)
        );
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithNullValue() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                new LWM2MResourceInstance(LWM2MID.from(1), (byte[]) null)
        );
    }

    @SuppressWarnings("unused")
    public void createWithEmptyValue() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.from(1), new byte[0]);
        assertThat(instance.getValue().length, equalTo(0));
        assertThat(instance.getStringValue(), equalTo(""));

        instance = new LWM2MResourceInstance(LWM2MID.from(1), "");
        assertThat(instance.getStringValue(), equalTo(""));
        assertThat(instance.getValue().length, equalTo(0));
    }

    @Test
    public void createWithNegativeID() throws Exception {
        LWM2MResourceInstance instance = new LWM2MResourceInstance(LWM2MID.create(), 42);
        assertEquals(-1, instance.getId().intValue());
    }

}
