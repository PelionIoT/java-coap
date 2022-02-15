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
package com.mbed.lwm2m.json;

import static org.junit.jupiter.api.Assertions.*;
import com.mbed.lwm2m.LWM2MResourceType;
import org.junit.jupiter.api.Test;


/**
 * @author nordav01
 */
public class JsonResourceTest {

    private static final String NAME = "res";

    @Test
    public void testStringValue() {
        JsonResource resource = new JsonResource(NAME, "alma");
        assertEquals("alma", resource.getStringValue() );
        assertNull(resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertEquals("alma", resource.getValue() );

        assertEquals(LWM2MResourceType.STRING, resource.getType());
    }

    @Test
    public void testNumericalValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, 42);
        assertNull(resource.getStringValue());
        assertEquals(42, resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertEquals("42", resource.getValue() );

        assertEquals(LWM2MResourceType.INTEGER, resource.getType());
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, 42L).getType() );
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, (short) 42).getType() );
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, (byte) 42).getType() );
        assertEquals(LWM2MResourceType.FLOAT, new JsonResource(NAME, 42.0).getType() );
        assertEquals(LWM2MResourceType.FLOAT, new JsonResource(NAME, (float) 42.0).getType() );
    }

    @Test
    public void testBooleanValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, true);
        assertNull(resource.getStringValue());
        assertNull(resource.getNumericalValue());
        assertTrue(resource.getBooleanValue());
        assertEquals("1", resource.getValue() );
        assertEquals("0", new JsonResource(NAME, false).getValue() );

        assertEquals(LWM2MResourceType.BOOLEAN, resource.getType());
    }

    @Test
    public void testNullValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, (String) null);
        assertNull(resource.getStringValue());
        assertNull(resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertNull(resource.getValue());
        assertNull(resource.getObjectLinkValue());
        assertNull(resource.getType());
    }

    @Test
    public void testTimeAttribute() throws Exception {
        JsonResource resource = new JsonResource(NAME, (String) null);
        assertNull (resource.getTime());

        resource.setTime(42);
        assertEquals(new Integer(42), resource.getTime());
    }

}
