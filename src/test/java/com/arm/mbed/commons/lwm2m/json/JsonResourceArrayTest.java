/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author nordav01
 */
public class JsonResourceArrayTest {

    @Test
    public void testBaseTime() {
        JsonResourceArray array = new JsonResourceArray();
        int bt = (int) (System.currentTimeMillis() / 1000);

        assertNull(array.getBaseTime());
        assertEquals("", array.toString());
        array.setBaseTime(bt);
        assertEquals(new Integer(bt), array.getBaseTime());

        System.out.println(array);
        assertTrue(array.toString().indexOf(String.valueOf(bt)) > 0);
    }

    @Test
    public void testBaseName() throws Exception {
        JsonResourceArray array = new JsonResourceArray();
        assertNull(array.getBaseName());
        assertEquals("", array.toString());
        array.setBaseName("/");
        assertEquals("/", array.getBaseName());

        System.out.println(array);
        assertThat(array.toString(), containsString("\"/\""));
    }

}
