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

import static com.mbed.coap.packet.DataConvertingUtility.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

public class DataConvertingUtilityTest {

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

}