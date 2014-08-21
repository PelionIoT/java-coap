/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import static org.junit.Assert.*;
import com.arm.mbed.commons.lwm2m.LWM2MResourceType;
import com.arm.mbed.commons.lwm2m.json.JsonResource;
import org.junit.Test;

/**
 * @author nordav01
 */
public class JsonResourceTest {

    private static final String NAME = "res";

    @Test
    public void testStringValue() {
        JsonResource resource = new JsonResource(NAME, "alma");
        assertEquals("alma", resource.getStringValue() );
        assertNull(resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertEquals("alma", resource.getValue() );
        
        assertEquals(LWM2MResourceType.STRING, resource.getType());
    }
    
    @Test
    public void testNumericalValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, 42);
        assertNull(resource.getStringValue());
        assertEquals(42, resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertEquals("42", resource.getValue() );
        
        assertEquals(LWM2MResourceType.INTEGER, resource.getType());
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, 42L).getType() );
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, (short) 42).getType() );
        assertEquals(LWM2MResourceType.INTEGER, new JsonResource(NAME, (byte) 42).getType() );
        assertEquals(LWM2MResourceType.FLOAT, new JsonResource(NAME, 42.0).getType() );
        assertEquals(LWM2MResourceType.FLOAT, new JsonResource(NAME, (float) 42.0).getType() );
    }
    
    @Test
    public void testBooleanValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, true);
        assertNull(resource.getStringValue());
        assertNull(resource.getNumericalValue());
        assertTrue(resource.getBooleanValue());
        assertEquals("1", resource.getValue() );
        assertEquals("0", new JsonResource(NAME, false).getValue() );
        
        assertEquals(LWM2MResourceType.BOOLEAN, resource.getType());
    }
    
    @Test
    public void testNullValue() throws Exception {
        JsonResource resource = new JsonResource(NAME, (String) null);
        assertNull(resource.getStringValue());
        assertNull(resource.getNumericalValue());
        assertNull(resource.getBooleanValue());
        assertNull(resource.getValue());
        assertNull(resource.getType());
    }
    
    @Test
    public void testTimeAttribute() throws Exception {
        JsonResource resource = new JsonResource(NAME, (String) null);
        assertNull (resource.getTime());
        
        resource.setTime(42);
        assertEquals(new Integer(42), resource.getTime());
    }

}
