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

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class LWM2MObjectInstanceTest {

    @SuppressWarnings("unused")
    @Test
    public void createWithNullID() {
        assertThrows(NullPointerException.class, () ->
                new LWM2MObjectInstance((LWM2MID) null, new LWM2MResource(LWM2MID.$0, 0))
        );
    }

    @Test
    public void getResource() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(
                new LWM2MResource(LWM2MID.$1, 42),
                new LWM2MResource(LWM2MID.from("dev"), 56));

        System.out.println(instance);
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue());
        assertEquals("56", instance.getResource(LWM2MID.from("dev")).getStringValue());
        assertNull(instance.getResource(LWM2MID.$0));
    }

    @Test
    public void addResource() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(new LWM2MResource(LWM2MID.from("dev"), 56));
        instance.addResource(new LWM2MResource(LWM2MID.$1, 42));
        assertEquals(2, instance.getResources().size());
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue());
    }

    @Test
    public void addResources() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(new LWM2MResource(LWM2MID.from("dev"), 56));
        instance.addResources(Arrays.asList(new LWM2MResource(LWM2MID.$1, 42)));
        assertEquals(2, instance.getResources().size());
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue());
    }

}
