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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import com.mbed.lwm2m.LWM2MID;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import com.mbed.lwm2m.LWM2MResourceType;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JsonSerializerTest {

    private JsonSerializer serializer;

    @BeforeEach
    public void setup() {
        serializer = JsonSerializer.create();
    }

    @Test
    public void serializeSimpleResourceWithNoType() {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "Simple Resource");
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"1\",\"sv\":\"Simple Resource\"}]}"));
    }

    @Test
    public void serializeSimpleStringResource() {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "Simple String");
        resource.setType(LWM2MResourceType.STRING);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"1\",\"sv\":\"Simple String\"}]}"));
    }

    @Test
    public void serializeSimpleIntegerResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "1024");
        resource.setType(LWM2MResourceType.INTEGER);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"1\",\"v\":1024}]}"));
    }

    @Test
    public void serializeSimpleDoubleResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "5.0");
        resource.setType(LWM2MResourceType.FLOAT);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"1\",\"v\":5.0}]}"));
    }

    @Test
    public void serializeMultipleResource() throws Exception {
        LWM2MResourceInstance inst0 = new LWM2MResourceInstance(LWM2MID.from("0"), "Instance One");
        LWM2MResourceInstance inst1 = new LWM2MResourceInstance(LWM2MID.from("1"), "2");
        inst1.setType(LWM2MResourceType.INTEGER);
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("0"), inst0, inst1);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"0/0\",\"sv\":\"Instance One\"},{\"n\":\"0/1\",\"v\":2}]}"));
    }

    @Test
    public void serializeMultipleResourceStringId() throws Exception {
        LWM2MResourceInstance inst0 = new LWM2MResourceInstance(LWM2MID.from("today"), "Instance One");
        LWM2MResourceInstance inst1 = new LWM2MResourceInstance(LWM2MID.from("yesterday"), "2");
        inst1.setType(LWM2MResourceType.INTEGER);
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("0"), inst0, inst1);
        String json = serializer.serialize(resource);
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"0/today\",\"sv\":\"Instance One\"},{\"n\":\"0/yesterday\",\"v\":2}]}"));
    }

    @Test
    public void serializeBooleanResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.$0, 1);
        resource.setType(LWM2MResourceType.BOOLEAN);
        String json = serializer.serialize(resource);
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"0\",\"bv\":true}]}"));

        resource = new LWM2MResource(LWM2MID.$0, 0);
        resource.setType(LWM2MResourceType.BOOLEAN);
        json = serializer.serialize(resource);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"0\",\"bv\":false}]}"));
    }

    @Test
    public void serializeOpaqueResource() throws Exception {
        byte[] opaque = new byte[]{1, 2, 3, 4};
        LWM2MResource resource = new LWM2MResource(LWM2MID.$0, opaque);
        String json = serializer.serialize(resource);
        System.out.println(Base64.getEncoder().encodeToString(opaque));
        System.out.println(json);
        assertThat(json, equalTo("{\"e\":[{\"n\":\"0\",\"sv\":\"AQIDBA==\"}]}"));
    }

}
