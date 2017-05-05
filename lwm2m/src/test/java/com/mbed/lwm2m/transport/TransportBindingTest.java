/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
import org.junit.Test;

public class TransportBindingTest {

    @Test
    public void parse() throws TransportBindingParseException {
        assertEquals(new TransportBinding(true, false, false), TransportBinding.parse("U"));
        assertEquals(new TransportBinding(false, true, false), TransportBinding.parse("S"));
        assertEquals(new TransportBinding(true, false, true), TransportBinding.parse("UQ"));
        assertEquals(new TransportBinding(true, true, true), TransportBinding.parse("UQS"));
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

    private static void assertParseFails(String transBinding) {
        try {
            TransportBinding.parse(transBinding);
            fail();
        } catch (TransportBindingParseException ex) {
            //expected
        }
    }
}
