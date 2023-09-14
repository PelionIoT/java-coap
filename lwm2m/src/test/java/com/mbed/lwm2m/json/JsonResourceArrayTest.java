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
package com.mbed.lwm2m.json;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author nordav01
 */
public class JsonResourceArrayTest {

    @Test
    public void testBaseTime() {
        JsonResourceArray array = new JsonResourceArray();
        int bt = (int) (System.currentTimeMillis() / 1000);

        assertNull(array.getBaseTime());
        assertEquals("", array.toString());
        array.setBaseTime(bt);
        assertEquals(new Integer(bt), array.getBaseTime());

        System.out.println(array);
        assertTrue(array.toString().indexOf(String.valueOf(bt)) > 0);
    }

    @Test
    public void testBaseName() throws Exception {
        JsonResourceArray array = new JsonResourceArray();
        assertNull(array.getBaseName());
        assertEquals("", array.toString());
        array.setBaseName("/");
        assertEquals("/", array.getBaseName());

        System.out.println(array);
        assertThat(array.toString(), containsString("\"/\""));
    }

}
