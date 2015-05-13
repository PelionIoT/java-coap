/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.json;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import com.arm.mbed.commons.lwm2m.LWM2MResource;
import com.arm.mbed.commons.lwm2m.LWM2MResourceType;
import com.arm.mbed.commons.string.Utf8Bytes;
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
        assertArrayEquals(Utf8Bytes.of("Open Mobile Alliance"), resources.get(0).getValue());
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
