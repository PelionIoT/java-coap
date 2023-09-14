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
package com.mbed.coap.utils;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author szymon
 */
public class HexArrayTest {

    @Test
    public void toHex() {
        assertEquals("0102030405060708090a0b0c", HexArray.toHex(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));

        assertEquals("0102030405060708090a0b0c", new HexArray(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}).toString());
        assertEquals("01020304..", HexArray.toHexShort(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 4));
        assertEquals("0102030405", HexArray.toHexShort(new byte[]{1, 2, 3, 4, 5}, 5));
    }

    @Test
    public void testEquals() {
        HexArray hexArray = new HexArray(new byte[]{1, 2, 3, 4});

        assertEquals(hexArray, new HexArray(new byte[]{1, 2, 3, 4}));
        assertNotEquals(hexArray, new HexArray(new byte[]{1}));
        assertNotEquals(hexArray, "");
        assertNotEquals(hexArray, null);

        assertEquals(hexArray.hashCode(), new HexArray(new byte[]{1, 2, 3, 4}).hashCode());
    }

    @Test
    public void fromHex() {
        assertArrayEquals(new byte[]{1}, HexArray.fromHex("01"));
        assertArrayEquals(new byte[]{((byte) 0xFF)}, HexArray.fromHex("ff"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, HexArray.fromHex("0102030405060708090a0b0c"));
    }

    @Test
    public void malformedHex() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> HexArray.fromHex("0g"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> HexArray.fromHex("0A"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> HexArray.fromHex("dupa"));
    }

}
