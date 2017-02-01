/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author szymon
 */
public class TransportContextTest {

    @Test
    public void test() {
        TransportContext tc = TransportContext.NULL.add("1", () -> "1");

        assertEquals(tc.get("1"), "1");
        assertNull(tc.get("2"));
        assertNull(tc.get(null));

        assertEquals("1", tc.getAndCast("1", String.class));
        assertNull(tc.getAndCast("1", Integer.class));
        assertNull(tc.getAndCast(null, Integer.class));
    }

    @Test
    public void testNested() {
        TransportContext tcFirst = TransportContext.NULL.add("1", "1");
        TransportContext tc = tcFirst.add("2", () -> "2");

        assertEquals(tc.get("1"), "1");
        assertEquals(tc.get("2"), "2");
        assertNull(tc.get("3"));
        assertNull(tc.get(null));
        assertNull(tcFirst.get("2"));

        assertEquals("2", tc.getAndCast("2", String.class));
        assertNull(tc.getAndCast("2", Integer.class));
        assertNull(tc.getAndCast(null, Integer.class));
    }

}
