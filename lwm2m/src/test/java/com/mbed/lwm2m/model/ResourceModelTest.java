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
package com.mbed.lwm2m.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ResourceModelTest {

    @Test
    public void testResourceModelAttributes() throws Exception {
        ResourceModel resource = new ResourceModel("1", "name", "RW", Instances.SINGLE, Type.TIME, "", "description");
        assertEquals("1", resource.getResourceID());
        assertEquals("name", resource.getResourceName());
        assertEquals("RW", resource.getOperations());
        assertEquals(Instances.SINGLE, resource.getInstances());
        assertEquals(Type.TIME, resource.getType());
        assertEquals("", resource.getRange());
        assertEquals("description", resource.getDescription());
        assertTrue(resource.isReadable());
        assertTrue(resource.isWriteable());
        assertFalse(resource.isExecutable());
        assertNotNull(resource.toString());
    }

    @Test
    public void validateStringEmptyRange() throws Exception {
        ResourceModel stringRes = createResourceModel(Type.STRING, "");
        assertTrue(stringRes.isValid(""));
        assertTrue(stringRes.isValid("hello"));
    }

    @Test
    public void validateStringLengthUnspecified() throws Exception {
        validateStringLengthUnspecified(createResourceModel(Type.STRING, "{}"));
        validateStringLengthUnspecified(createResourceModel(Type.STRING, ""));
        validateStringLengthUnspecified(createResourceModel(Type.STRING, "{]"));
        validateStringLengthUnspecified(createResourceModel(Type.STRING, "[}"));
    }

    private static void validateStringLengthUnspecified(ResourceModel stringRes) {
        assertFalse(stringRes.isValid((String) null));
        assertFalse(stringRes.isValid((byte[]) null));
        assertTrue(stringRes.isValid((new byte[0])));
        assertTrue(stringRes.isValid(""));
        assertTrue(stringRes.isValid("hello"));
    }

    @Test
    public void validateStringLength() throws Exception {
        ResourceModel stringRes = createResourceModel(Type.STRING, "{5}");
        assertFalse(stringRes.isValid("hey"));
        assertTrue(stringRes.isValid("hello"));
        assertFalse(stringRes.isValid("helloka"));
    }

    @Test
    public void validateStringLengthRange() {
        ResourceModel stringRes = createResourceModel(Type.STRING, "{1,9}");
        assertFalse(stringRes.isValid(1));
        assertTrue(stringRes.isValid("hello"));
        assertFalse(stringRes.isValid(""));
        assertFalse(stringRes.isValid("hellobello"));
    }

    @Test
    public void validateStringOptions() throws Exception {
        ResourceModel stringRes = createResourceModel(Type.STRING, "[U,UQ,S,SQ]");
        assertTrue(stringRes.isValid("U"));
        assertTrue(stringRes.isValid("UQ"));
        assertTrue(stringRes.isValid("S"));
        assertTrue(stringRes.isValid("SQ"));
        assertFalse(stringRes.isValid("Q"));
    }

    @Test
    public void validateIntegerRange() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "[1-7]");
        assertFalse(intRes.isValid("hi"));
        assertTrue(intRes.isValid(1));
        assertTrue(intRes.isValid("7"));
        assertFalse(intRes.isValid(0));
        assertFalse(intRes.isValid(8));
    }

    @Test
    public void validateIntegerRange16bit() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "[1-65535]");
        assertFalse(intRes.isValid("hi"));
        assertTrue(intRes.isValid(1));
        assertTrue(intRes.isValid("7"));
        assertFalse(intRes.isValid(0));
        assertTrue(intRes.isValid(new byte[]{0x01, (byte) 0xFF}));
        assertTrue(intRes.isValid("1024"));
        assertTrue(intRes.isValid(new byte[]{(byte) 0xFF, (byte) 0xFF}));
        assertFalse(intRes.isValid(65536));
    }

    @Test
    public void validateIntegerRangeNoLowerBound() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "[-0]");
        assertFalse(intRes.isValid("hi"));
        assertTrue(intRes.isValid(0));
        assertTrue(intRes.isValid(-1));
        assertTrue(intRes.isValid(Integer.MIN_VALUE));
        assertFalse(intRes.isValid("1"));
    }

    @Test
    public void validateIntegerOptions() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "[1,2,3]");
        assertFalse(intRes.isValid("hey"));
        assertTrue(intRes.isValid(1));
        assertTrue(intRes.isValid("3"));
        assertFalse(intRes.isValid(0));
        assertFalse(intRes.isValid(4));
    }

    @Test
    public void validateIntegerOptionsSkipNonIntegers() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "[1,2a,3]");
        assertTrue(intRes.isValid(1));
        assertFalse(intRes.isValid(2));
        assertTrue(intRes.isValid(3));
    }

    @Test
    public void validateIntegerAny() throws Exception {
        ResourceModel intRes = createResourceModel(Type.INTEGER, "");
        assertTrue(intRes.isValid(1));
        assertTrue(intRes.isValid(Integer.MAX_VALUE));
        assertTrue(intRes.isValid(Integer.MIN_VALUE));
        assertFalse(intRes.isValid(new byte[5]));
        assertFalse(intRes.isValid(new byte[0]));
    }

    @Test
    public void validateBooleanResource() throws Exception {
        ResourceModel bolRes = createResourceModel(Type.BOOLEAN, "");
        assertTrue(bolRes.isValid(0));
        assertTrue(bolRes.isValid(1));
        assertTrue(bolRes.isValid("0"));
        assertTrue(bolRes.isValid("1"));
        assertFalse(bolRes.isValid(2));
    }

    @Test
    public void validateTimeResource() throws Exception {
        ResourceModel timeRes = createResourceModel(Type.TIME, "");
        assertTrue(timeRes.isValid(0));
        assertTrue(timeRes.isValid(10000));
        assertFalse(timeRes.isValid(-1));
    }

    @Test
    public void validateOpaqueResource() throws Exception {
        ResourceModel binRes = createResourceModel(Type.OPAQUE, "{2,4}");
        assertFalse(binRes.isValid(new byte[1]));
        assertTrue(binRes.isValid(new byte[3]));
        assertFalse(binRes.isValid(new byte[5]));

        assertFalse(binRes.isValid(0));
        assertFalse(binRes.isValid(127));
        assertFalse(binRes.isValid(255));
        assertTrue(binRes.isValid(256));
        assertTrue(binRes.isValid(65535));
        assertTrue(binRes.isValid(65536));
        assertTrue(binRes.isValid(16777215));
        assertTrue(binRes.isValid(16777216));

        assertFalse(binRes.isValid((String) null));
        assertFalse(binRes.isValid(""));
        assertFalse(binRes.isValid("q"));
        assertTrue(binRes.isValid("up"));
        assertTrue(binRes.isValid("eos"));
        assertTrue(binRes.isValid("golf"));
        assertFalse(binRes.isValid("turan"));
    }

    @Test
    public void validateOpaqueResourceFixedSize() throws Exception {
        ResourceModel binRes = createResourceModel(Type.OPAQUE, "{2}");
        assertFalse(binRes.isValid(new byte[1]));
        assertTrue(binRes.isValid(new byte[2]));
        assertFalse(binRes.isValid(new byte[3]));

        assertFalse(binRes.isValid(0));
        assertFalse(binRes.isValid(255));
        assertTrue(binRes.isValid(256));
        assertTrue(binRes.isValid(65535));
        assertFalse(binRes.isValid(65536));

        assertFalse(binRes.isValid((String) null));
        assertFalse(binRes.isValid(""));
        assertFalse(binRes.isValid("q"));
        assertTrue(binRes.isValid("up"));
        assertFalse(binRes.isValid("eos"));
    }

    @Test
    public void validateOpaqueResourceAnySize() throws Exception {
        ResourceModel binRes = createResourceModel(Type.OPAQUE, "{}");
        validateOpaqueResourceAnySize(binRes);
    }

    @Test
    public void validateOpaqueResourceRangeUndefined() throws Exception {
        ResourceModel binRes = createResourceModel(Type.OPAQUE, "");
        validateOpaqueResourceAnySize(binRes);
    }

    private static void validateOpaqueResourceAnySize(ResourceModel binRes) {
        assertFalse(binRes.isValid((byte[]) null));
        assertTrue(binRes.isValid(new byte[0]));
        assertTrue(binRes.isValid(new byte[1]));
        assertTrue(binRes.isValid(new byte[2]));
        assertTrue(binRes.isValid(new byte[3]));

        assertTrue(binRes.isValid(0));
        assertTrue(binRes.isValid(255));
        assertTrue(binRes.isValid(256));
        assertTrue(binRes.isValid(65535));
        assertTrue(binRes.isValid(65536));

        assertFalse(binRes.isValid((String) null));
        assertTrue(binRes.isValid(""));
        assertTrue(binRes.isValid("q"));
        assertTrue(binRes.isValid("up"));
        assertTrue(binRes.isValid("eos"));
    }

    @Test
    public void validateExecutableResource() throws Exception {
        ResourceModel exeRes = createResourceModel(Type.EXECUTABLE, null);
        assertFalse(exeRes.isValid(new byte[1]));
        assertFalse(exeRes.isValid(1));
        assertFalse(exeRes.isValid("1"));
    }

    private static ResourceModel createResourceModel(Type type, String range) {
        return new ResourceModel("", "", "", null, type, range, "");
    }

}
