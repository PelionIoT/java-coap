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
package com.mbed.lwm2m;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * @author nordav01
 */
public class LWM2MObjectTest {

    private final List<LWM2MObjectInstance> instances = new ArrayList<>();

    @SuppressWarnings("unused")
    @Test(expected=NullPointerException.class)
    public void createWithNullID() {
        new LWM2MObject(null, instances);
    }

    @Test
    public void getInstances() throws Exception {
        LWM2MObject object = new LWM2MObject(
                new LWM2MObjectInstance(LWM2MID.$1, new ArrayList<LWM2MResource>() ),
                new LWM2MObjectInstance(LWM2MID.$2, new ArrayList<LWM2MResource>() )
                );

        System.out.println(object.toString());
        assertEquals(2, object.getInstances().size());
        assertEquals(LWM2MID.$1, object.getInstanceFor(LWM2MID.$1).getId());
        assertEquals(LWM2MID.$2, object.getInstanceFor(LWM2MID.$2).getId());
        assertNull(object.getInstanceFor(LWM2MID.$0));
    }

    @Test
    public void addInstance() throws Exception {
        LWM2MObject object = new LWM2MObject(LWM2MID.$0,
                new LWM2MObjectInstance(LWM2MID.$1, new ArrayList<LWM2MResource>() ) );
        object.addInstance(new LWM2MObjectInstance(LWM2MID.$2, new ArrayList<LWM2MResource>() ));

        assertEquals(0, object.getId().intValue() );
        assertEquals(2, object.getInstances().size());
        assertEquals(LWM2MID.$1, object.getInstanceFor(LWM2MID.$1).getId());
        assertEquals(LWM2MID.$2, object.getInstanceFor(LWM2MID.$2).getId());
    }

    @Test
    public void addInstances() throws Exception {
        LWM2MObject object = new LWM2MObject(LWM2MID.$0,
                new LWM2MObjectInstance(LWM2MID.$1, new ArrayList<LWM2MResource>() ) );
        object.addInstances(Arrays.asList(new LWM2MObjectInstance(LWM2MID.$2, new ArrayList<LWM2MResource>() )));

        assertEquals(0, object.getId().intValue() );
        assertEquals(2, object.getInstances().size());
        assertEquals(LWM2MID.$1, object.getInstanceFor(LWM2MID.$1).getId());
        assertEquals(LWM2MID.$2, object.getInstanceFor(LWM2MID.$2).getId());
    }

}
