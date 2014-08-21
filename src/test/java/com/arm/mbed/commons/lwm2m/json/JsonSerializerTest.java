/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import com.arm.mbed.commons.lwm2m.LWM2MID;
import com.arm.mbed.commons.lwm2m.LWM2MResource;
import com.arm.mbed.commons.lwm2m.LWM2MResourceInstance;
import com.arm.mbed.commons.lwm2m.LWM2MResourceType;
import com.arm.mbed.commons.lwm2m.json.JsonSerializer;
import com.google.common.io.BaseEncoding;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nordav01
 *
 */
public class JsonSerializerTest {
    
    private JsonSerializer serializer;

    @Before
    public void setup() {
        serializer = JsonSerializer.create();
    }

    @Test
    public void serializeSimpleResourceWithNoType() {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "Simple Resource");
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"1\",\"sv\":\"Simple Resource\"}]}"));
    }

    @Test
    public void serializeSimpleStringResource() {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "Simple String");
        resource.setType(LWM2MResourceType.STRING);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"1\",\"sv\":\"Simple String\"}]}"));
    }
    
    @Test
    public void serializeSimpleIntegerResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "1024");
        resource.setType(LWM2MResourceType.INTEGER);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"1\",\"v\":1024}]}"));
    }
    
    @Test
    public void serializeSimpleDoubleResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("1"), "5.0");
        resource.setType(LWM2MResourceType.FLOAT);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"1\",\"v\":5.0}]}"));
    }
    
    @Test
    public void serializeMultipleResource() throws Exception {
        LWM2MResourceInstance inst0 = new LWM2MResourceInstance(LWM2MID.from("0"), "Instance One");
        LWM2MResourceInstance inst1 = new LWM2MResourceInstance(LWM2MID.from("1"), "2");
        inst1.setType(LWM2MResourceType.INTEGER);
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("0"), inst0, inst1);
        String json = serializer.serialize(Arrays.asList(resource));
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"0/0\",\"sv\":\"Instance One\"},{\"n\":\"0/1\",\"v\":2}]}"));
    }
    
    @Test
    public void serializeMultipleResourceStringId() throws Exception {
        LWM2MResourceInstance inst0 = new LWM2MResourceInstance(LWM2MID.from("today"), "Instance One");
        LWM2MResourceInstance inst1 = new LWM2MResourceInstance(LWM2MID.from("yesterday"), "2");
        inst1.setType(LWM2MResourceType.INTEGER);
        LWM2MResource resource = new LWM2MResource(LWM2MID.from("0"), inst0, inst1);
        String json = serializer.serialize(resource);
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"0/today\",\"sv\":\"Instance One\"},{\"n\":\"0/yesterday\",\"v\":2}]}"));
    }
    
    @Test
    public void serializeBooleanResource() throws Exception {
        LWM2MResource resource = new LWM2MResource(LWM2MID.$0, 1);
        resource.setType(LWM2MResourceType.BOOLEAN);
        String json = serializer.serialize(resource);
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"0\",\"bv\":true}]}") );
        
        resource = new LWM2MResource(LWM2MID.$0, 0);
        resource.setType(LWM2MResourceType.BOOLEAN);
        json = serializer.serialize(resource);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"0\",\"bv\":false}]}") );
    }
    
    @Test
    public void serializeOpaqueResource() throws Exception {
        byte[] opaque = new byte[]{1,2,3,4};
        LWM2MResource resource = new LWM2MResource(LWM2MID.$0, opaque);
        String json = serializer.serialize(resource);
        System.out.println(BaseEncoding.base64().encode(opaque) );
        System.out.println(json);
        assertThat (json, equalTo("{\"e\":[{\"n\":\"0\",\"sv\":\"AQIDBA==\"}]}"));
    }

}
