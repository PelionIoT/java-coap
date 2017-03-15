/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m;

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
