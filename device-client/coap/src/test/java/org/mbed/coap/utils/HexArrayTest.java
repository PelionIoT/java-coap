/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class HexArrayTest {

    @Test
    public void toHex() {
        assertEquals("0102030405060708090a0b0c", HexArray.toHex(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));

        assertEquals("0102030405060708090a0b0c", new HexArray(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}).toString());
        assertEquals("01020304..", HexArray.toHexShort(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 4));
        assertEquals("0102030405", HexArray.toHexShort(new byte[]{1, 2, 3, 4, 5}, 5));
    }

    @Test
    public void fromHex() throws Exception {
        assertEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, HexArray.fromHex("0102030405060708090a0b0c"));
    }

}
