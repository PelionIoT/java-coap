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
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceType;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nordav01
 */
public class JsonDeserializerTest {

    private static final String DEVICE_OBJECT_JSON = "device-object.json";
    private static final String NOTIFICATION_JSON = "notification.json";
    private static final String CUSTOM_OBJECT_JSON = "custom-object.json";

    private JsonDeserializer deserializer;

    @Before
    public void setup() {
        deserializer = JsonDeserializer.create();
    }

    @Test
    public void parseDeviceObject() throws Exception {
        InputStream input = JsonDeserializerTest.class.getResourceAsStream(DEVICE_OBJECT_JSON);
        String jsonString = IOUtils.toString(input);
        List<LWM2MResource> resources = deserializer.deserialize(jsonString);

        assertThat(resources, hasSize(13));
        assertThat(resources.get(0).getType(), equalTo(LWM2MResourceType.STRING));
        assertArrayEquals("Open Mobile Alliance".getBytes(), resources.get(0).getValue());
        assertThat(resources.get(3).getType(), equalTo(LWM2MResourceType.STRING));
        assertThat(resources.get(4).getNestedInstances().get(0).getType(), equalTo(LWM2MResourceType.INTEGER));
    }

    @Test
    public void parseNotification() throws Exception {
        InputStream input = JsonDeserializerTest.class.getResourceAsStream(NOTIFICATION_JSON);
        String jsonString = IOUtils.toString(input);
        List<LWM2MResource> resources = deserializer.deserialize(jsonString);

        assertThat(resources, hasSize(1));
        assertThat(resources.get(0).getType(), nullValue());
        assertThat(resources.get(0).getNestedInstances(), hasSize(3));
        assertThat(resources.get(0).getNestedInstances().get(0).getType(), equalTo(LWM2MResourceType.FLOAT));
    }

    @Test
    public void parseCustomObject() throws Exception {
        InputStream input = JsonDeserializerTest.class.getResourceAsStream(CUSTOM_OBJECT_JSON);
        String jsonString = IOUtils.toString(input);
        List<LWM2MResource> resources = deserializer.deserialize(jsonString);
        System.out.println(resources);
    }

}
