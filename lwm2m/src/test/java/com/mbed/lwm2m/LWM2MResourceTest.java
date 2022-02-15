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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LWM2MResourceTest {

    @SuppressWarnings("unused")
    @Test
    public void createWithNullID() {
        assertThrows(NullPointerException.class, () ->
                new LWM2MResource(null, 42)
        );
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithNegativeID() {
        assertThrows(IllegalArgumentException.class, () ->
                new LWM2MResource(LWM2MID.from(-1), 42)
        );
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithLongID() {
        assertThrows(IllegalArgumentException.class, () ->
                new LWM2MResource(LWM2MID.from(65536), 42)
        );
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithNullNestedResourceInstances() {
        assertThrows(NullPointerException.class, () ->
                new LWM2MResource(LWM2MID.from(1), (List<LWM2MResourceInstance>) null)
        );
    }

    @SuppressWarnings("unused")
    @Test
    public void createWithEmptyNestedResourceInstances() {
        List<LWM2MResourceInstance> instances = Collections.emptyList();
        assertThrows(IllegalArgumentException.class, () ->
                new LWM2MResource(LWM2MID.from(1), instances)
        );
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
