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
package com.mbed.coap.packet;

import static com.mbed.coap.packet.DataConvertingUtility.*;
import static com.mbed.coap.utils.HexArray.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Lists;
import org.junit.Test;

/**
 * Created by szymon
 */
public class DataConvertingUtilityTest {

    @Test
    public void readVariableULongTest() throws Exception {

        assertEquals(1, readVariableULong(new byte[]{1}).longValue());
        assertEquals(0xf1, readVariableULong(new byte[]{(byte) 0xf1}).longValue());
        assertTrue(readVariableULong(new byte[]{(byte) 0xf1}) > 0);

        assertEquals(0x0101, readVariableULong(new byte[]{1, 1}).longValue());
        assertEquals(0x010101, readVariableULong(new byte[]{1, 1, 1}).longValue());
        assertEquals(0x01010101, readVariableULong(new byte[]{1, 1, 1, 1}).longValue());

        assertEquals(0xff0101, readVariableULong(new byte[]{((byte) 0xff), 1, 1}).longValue());

        assertNull(readVariableULong(null));

    }

    @Test
    public void testParseUriQuery() throws ParseException {
        Map<String, String> q = new HashMap<>();
        q.put("par1", "12");

        assertEquals(q, parseUriQuery("par1=12"));
        assertEquals(q, parseUriQuery("?par1=12"));

        q.put("par2", "14");
        assertEquals(q, parseUriQuery("par1=12&par2=14"));
        assertEquals(q, parseUriQuery("?par1=12&par2=14"));

        q.put("d", "b");
        assertEquals(q, parseUriQuery("par1=12&par2=14&d=b"));

        assertNull(parseUriQuery(null));
        assertNull(parseUriQuery(""));

        assertThatThrownBy(() -> parseUriQuery("p=aa&par133")).isExactlyInstanceOf(ParseException.class);
    }


    @Test
    public void splitTest() throws Exception {
        assertArrayEquals(new String[]{"", "test1", "test2", "test3"},
                DataConvertingUtility.split("/test1/test2/test3", '/'));

        assertArrayEquals(new String[]{"", "test1", "", "test3"},
                DataConvertingUtility.split("/test1//test3", '/'));

        assertArrayEquals(new String[]{"aa"},
                DataConvertingUtility.split("aa", '/'));

    }

    @Test
    public void testParseMultiUriQuery() throws ParseException {
        Map<String, List<String>> q = new HashMap<>();
        q.put("par1", Lists.newArrayList("12"));
        assertEquals(q, parseUriQueryMult("par1=12"));

        Map<String, List<String>> q2 = new HashMap<>();
        q2.put("par1", Lists.newArrayList("11", "22"));
        assertEquals(q2, parseUriQueryMult("par1=11&par1=22"));

        assertNull(parseUriQueryMult(null));
        assertNull(parseUriQueryMult(""));

        assertThatThrownBy(() -> parseUriQueryMult("p=aa&par133")).isExactlyInstanceOf(ParseException.class);
    }

    @Test
    public void testConvertVariableUInt() {
        assertArrayEquals(fromHex("00"), convertVariableUInt(0x00));
        assertArrayEquals(fromHex("02"), convertVariableUInt(0x02));
        assertArrayEquals(fromHex("ff"), convertVariableUInt(0xFF));
        assertArrayEquals(fromHex("0100"), convertVariableUInt(0x0100));
        assertArrayEquals(fromHex("0203"), convertVariableUInt(0x0203));
        assertArrayEquals(fromHex("ffff"), convertVariableUInt(0xFFFF));
        assertArrayEquals(fromHex("010000"), convertVariableUInt(0x010000));
        assertArrayEquals(fromHex("020304"), convertVariableUInt(0x020304));
        assertArrayEquals(fromHex("02030405"), convertVariableUInt(0x02030405));

    }

}