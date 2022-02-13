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
package com.mbed.coap.packet;

import static org.junit.jupiter.api.Assertions.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class OpaqueTest {

    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(Opaque.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }

    @Test
    public void stringConverting() {
        assertEquals("", Opaque.EMPTY.toUtf8String());
        assertEquals("dupa", Opaque.of("dupa").toUtf8String());
    }

    @Test
    public void testConvertVariableUInt() {
        assertEquals("00", Opaque.variableUInt(0x00).toHex());
        assertEquals("02", Opaque.variableUInt(0x02).toHex());
        assertEquals("ff", Opaque.variableUInt(0xFF).toHex());
        assertEquals("0100", Opaque.variableUInt(0x0100).toHex());
        assertEquals("0203", Opaque.variableUInt(0x0203).toHex());
        assertEquals("ffff", Opaque.variableUInt(0xFFFF).toHex());
        assertEquals("010000", Opaque.variableUInt(0x010000).toHex());
        assertEquals("020304", Opaque.variableUInt(0x020304).toHex());
        assertEquals("02030405", Opaque.variableUInt(0x02030405).toHex());
        assertEquals("0203040506", Opaque.variableUInt(0x0203040506L).toHex());
        assertEquals("020304050607", Opaque.variableUInt(0x020304050607L).toHex());
        assertEquals("02030405060708", Opaque.variableUInt(0x02030405060708L).toHex());
        assertEquals("0203040506070809", Opaque.variableUInt(0x0203040506070809L).toHex());
    }

    @Test
    public void shouldConvertToLong() {
        assertEquals(0, Opaque.EMPTY.toLong());
        assertEquals(0, Opaque.ofBytes(0).toLong());
        assertEquals(1, Opaque.ofBytes(1).toLong());
        assertEquals(0xf1, Opaque.ofBytes(0xf1).toLong());
        assertTrue(Opaque.ofBytes(0xf1).toLong() > 0);

        assertEquals(0x0101, Opaque.ofBytes(1, 1).toLong());
        assertEquals(0x010101, Opaque.ofBytes(1, 1, 1).toLong());
        assertEquals(0x01010101, Opaque.ofBytes(1, 1, 1, 1).toLong());

        assertEquals(0xff0101, Opaque.ofBytes(0xff, 1, 1).toLong());
    }

    @Test
    public void shouldFailToConvertToLongWhenTooMuchData() {
        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(1, 2, 3, 4, 5, 6, 7, 8, 9).toLong());

        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(1, 2, 3, 4, 5).toInt());
    }

    @Test
    void shouldFailWhenByteIsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(-1000));
        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(256));
        assertThrows(IllegalArgumentException.class, () -> Opaque.ofBytes(25612));

    }

    @Test
    public void shouldEncodeToHex() {
        assertEquals("0102030405060708090a0b0c", Opaque.ofBytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).toHex());

        assertEquals("01020304..", Opaque.ofBytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).toHexShort(4));
        assertEquals("0102030405", Opaque.ofBytes(1, 2, 3, 4, 5).toHexShort(5));
    }

    @Test
    public void shouldDecodeHex() {
        assertEquals(Opaque.ofBytes(1), Opaque.decodeHex("01"));
        assertEquals(Opaque.ofBytes(0xFF), Opaque.decodeHex("ff"));
        assertEquals(Opaque.ofBytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Opaque.decodeHex("0102030405060708090a0b0c"));
    }

    @Test
    public void malformedHex() {
        assertThrows(IllegalArgumentException.class, () -> Opaque.decodeHex("0g"));

        assertThrows(IllegalArgumentException.class, () -> Opaque.decodeHex("0A"));

        assertThrows(IllegalArgumentException.class, () -> Opaque.decodeHex("dupa"));
    }
}
