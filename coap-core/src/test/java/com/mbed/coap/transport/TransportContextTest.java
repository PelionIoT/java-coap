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
package com.mbed.coap.transport;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author szymon
 */
public class TransportContextTest {

    @Test
    public void test() {
        TransportContext tc = TransportContext.NULL.add("1", () -> "1");

        assertEquals(tc.get("1"), "1");
        assertNull(tc.get("2"));
        assertNull(tc.get(null));

        assertEquals("1", tc.getAndCast("1", String.class));
        assertNull(tc.getAndCast("1", Integer.class));
        assertNull(tc.getAndCast(null, Integer.class));
    }

    @Test
    public void testNested() {
        TransportContext tcFirst = TransportContext.NULL.add("1", "1");
        TransportContext tc = tcFirst.add("2", () -> "2");

        assertEquals(tc.get("1"), "1");
        assertEquals(tc.get("2"), "2");
        assertNull(tc.get("3"));
        assertNull(tc.get(null));
        assertNull(tcFirst.get("2"));

        assertEquals("2", tc.getAndCast("2", String.class));
        assertNull(tc.getAndCast("2", Integer.class));
        assertNull(tc.getAndCast(null, Integer.class));
    }

}
