/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.mbed.lwm2m;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

/**
 * @author nordav01
 */
public class LWM2MObjectInstanceTest {

    @SuppressWarnings("unused")
    @Test(expected=NullPointerException.class)
    public void createWithNullID() {
        new LWM2MObjectInstance( (LWM2MID) null, new LWM2MResource(LWM2MID.$0, 0));
    }

    @Test
    public void getResource() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(
                new LWM2MResource(LWM2MID.$1, 42),
                new LWM2MResource(LWM2MID.from("dev"), 56));

        System.out.println(instance);
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue() );
        assertEquals("56", instance.getResource(LWM2MID.from("dev")).getStringValue() );
        assertNull(instance.getResource(LWM2MID.$0));
    }

    @Test
    public void addResource() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(new LWM2MResource(LWM2MID.from("dev"), 56));
        instance.addResource(new LWM2MResource(LWM2MID.$1, 42));
        assertEquals (2, instance.getResources().size());
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue() );
    }

    @Test
    public void addResources() throws Exception {
        LWM2MObjectInstance instance = new LWM2MObjectInstance(new LWM2MResource(LWM2MID.from("dev"), 56));
        instance.addResources(Arrays.asList(new LWM2MResource(LWM2MID.$1, 42)));
        assertEquals (2, instance.getResources().size());
        assertEquals("42", instance.getResource(LWM2MID.$1).getStringValue() );
    }

}
