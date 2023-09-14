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
package com.mbed.lwm2m.model;

import static com.mbed.lwm2m.model.Type.*;
import static org.junit.Assert.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nordav01
 */
public class ObjectRegistryTest {

    private static final String LWM2M_TEST_OBJECTS_JSON = "lwm2m-test-objects.json";
    private ObjectRegistry registry;

    @Before
    public void before() {
        registry = ObjectRegistry.createObjectRegistry();
    }

    @Test
    public void getTypeOfResourceOfMultipleObject() throws Exception {
        Type type = registry.getOmaResourceType("/0/0/0");
        assertEquals(STRING, type);
    }

    @Test
    public void getTypeOfResourceOfSingleObject() throws Exception {
        Type type = registry.getOmaResourceType("/3/0/4");
        assertEquals(EXECUTABLE, type);
    }

    @Test(expected = InvalidResourceURIException.class)
    public void getTypeOfResourceWithObjectURI() throws Exception {
        registry.getOmaResourceType("/3");
    }

    @Test
    public void getTypeOfUnknownResourceOfKnownObject() throws Exception {
        assertNull(registry.getOmaResourceType("/0/0/unknown"));
    }

    @Test
    public void getTypeOfResourceOfUnknownObject() throws Exception {
        assertNull(registry.getOmaResourceType("/.unknown//0"));
    }

    @Test(expected = InvalidResourceURIException.class)
    public void getTypeOfInvalidResourceURI() throws Exception {
        registry.getOmaResourceType("invalid");
    }

    @Test(expected = NullPointerException.class)
    public void getTypeOfNullResourceURI() throws Exception {
        registry.getOmaResourceType(null);
    }

    @Test
    public void addObjectModelsToObjectRegistry() throws Exception {
        int baseSize = registry.getObjectModels().size();
        InputStream stream = this.getClass().getResourceAsStream(LWM2M_TEST_OBJECTS_JSON);
        Reader reader = new InputStreamReader(stream);
        registry.addObjectModels(ObjectRegistry.createObjectRegistry(reader).getObjectModels());

        assertEquals(baseSize + 2, registry.getObjectModels().size());
        assertNotNull(registry.getObjectModel("test"));
        assertNotNull(registry.getObjectModel("try").getResourceModel("0"));

        stream = this.getClass().getResourceAsStream(LWM2M_TEST_OBJECTS_JSON);
        reader = new InputStreamReader(stream);
        registry.addObjectModels(ObjectRegistry.createObjectRegistry(reader).getObjectModels());
        assertEquals(baseSize + 2, registry.getObjectModels().size());
    }

}
