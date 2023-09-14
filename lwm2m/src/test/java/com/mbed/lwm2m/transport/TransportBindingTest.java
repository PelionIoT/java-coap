/*
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
package com.mbed.lwm2m.transport;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class TransportBindingTest {

    @Test
    public void parse() throws TransportBindingParseException {
        assertEquals(new TransportBinding(true, false, false), TransportBinding.parse("U"));
        assertEquals(new TransportBinding(false, true, false), TransportBinding.parse("S"));
        assertEquals(new TransportBinding(true, false, true), TransportBinding.parse("UQ"));
        assertEquals(new TransportBinding(true, true, true), TransportBinding.parse("UQS"));
        assertEquals(new TransportBinding(false, true, true), TransportBinding.parse("SQ"));
        assertEquals(new TransportBinding(true, true, false), TransportBinding.parse("US"));

        assertTrue(TransportBinding.parse("SQ").isQueueMode());
        assertTrue(TransportBinding.parse("SQ").isSMS());
        assertFalse(TransportBinding.parse("SQ").isUDP());
    }

    @Test
    public void parse_faile() {
        assertParseFails("u");
        assertParseFails("UQSQ");
        assertParseFails("USQ");
        assertParseFails("s");
        assertParseFails("Us");
        assertParseFails("");
        assertParseFails("UqS");
    }

    @Test
    public void toStringTest() throws Exception {
        assertEquals(new TransportBinding(true, false, false).toString(), "U");
        assertEquals(new TransportBinding(true, true, true).toString(), "UQS");
        assertEquals(new TransportBinding(false, true, true).toString(), "SQ");
        assertEquals(new TransportBinding(true, true, false).toString(), "US");
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(TransportBinding.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }


    private static void assertParseFails(String transBinding) {
        try {
            TransportBinding.parse(transBinding);
            fail();
        } catch (TransportBindingParseException ex) {
            //expected
        }
    }
}
