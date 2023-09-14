/*
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
package com.mbed.lwm2m;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LWM2MResourceTest {

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void createWithNullID() {
        new LWM2MResource(null, 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithNegativeID() {
        new LWM2MResource(LWM2MID.from(-1), 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithLongID() {
        new LWM2MResource(LWM2MID.from(65536), 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void createWithNullNestedResourceInstances() {
        new LWM2MResource(LWM2MID.from(1), (List<LWM2MResourceInstance>) null);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithEmptyNestedResourceInstances() {
        List<LWM2MResourceInstance> instances = Collections.emptyList();
        new LWM2MResource(LWM2MID.from(1), instances);
    }

    @Test
    public void testHasNested() throws Exception {
        List<LWM2MResourceInstance> instances = Arrays.asList(new LWM2MResourceInstance(LWM2MID.from(1), "instance"));
        LWM2MResource resource = new LWM2MResource(LWM2MID.from(1), instances);

        assertEquals(1, resource.getId().intValue());
        assertTrue(resource.hasNestedInstances());
        assertFalse(resource.hasValue());
        assertThat(resource.getNestedInstances(), hasSize(1));
        assertArrayEquals("instance".getBytes(), resource.getNestedInstances().get(0).getValue());
    }

}
