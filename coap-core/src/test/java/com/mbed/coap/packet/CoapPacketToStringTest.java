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
package com.mbed.coap.packet;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author szymon
 */
public class CoapPacketToStringTest {

    @Test
    public void testToString_withShortPayload() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setPayload("short");

        assertEquals("CON GET MID:0 URI:/test pl(5):0x73686f7274", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_TEXT_PLAIN);
        assertEquals("CON GET MID:0 URI:/test ContTp:0 pl:'short'", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_JSON);
        assertEquals("CON GET MID:0 URI:/test ContTp:50 pl:'short'", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_NANOSERVICE_TLV);
        assertEquals("CON GET MID:0 URI:/test ContTp:200 pl(5):0x73686f7274", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_OCTET__STREAM);
        assertEquals("CON GET MID:0 URI:/test ContTp:42 pl(5):0x73686f7274", cp.toString());

        //unknown
        cp.headers().setContentFormat((short) 4321);
        assertEquals("CON GET MID:0 URI:/test ContTp:4321 pl(5):0x73686f7274", cp.toString());
    }

    @Test
    public void testToString_withLongPayload() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setPayload("long payload long payload long payload long payload");

        assertEquals("CON GET MID:0 URI:/test pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c..", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_TEXT_PLAIN);
        assertEquals("CON GET MID:0 URI:/test ContTp:0 pl(51):'long payload long payload long payload long ..'", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_SENML__JSON);
        assertEquals("CON GET MID:0 URI:/test ContTp:64 pl(51):'long payload long payload long payload long ..'", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_NANOSERVICE_TLV);
        assertEquals("CON GET MID:0 URI:/test ContTp:200 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c..", cp.toString());

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_EXI);
        assertEquals("CON GET MID:0 URI:/test ContTp:47 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c..", cp.toString());

        //unknown
        cp.headers().setContentFormat((short) 5321);
        assertEquals("CON GET MID:0 URI:/test ContTp:5321 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c..", cp.toString());
    }

    @Test
    public void testToString_full_withLongPayload() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setPayload("long payload long payload long payload long payload");

        assertEquals("CON GET MID:0 URI:/test pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164", cp.toString(true));

        cp.headers().setContentFormat(MediaTypes.CT_TEXT_PLAIN);
        assertEquals("CON GET MID:0 URI:/test ContTp:0 pl:'long payload long payload long payload long payload'", cp.toString(true));

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_SENML__JSON);
        assertEquals("CON GET MID:0 URI:/test ContTp:64 pl:'long payload long payload long payload long payload'", cp.toString(true));

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_NANOSERVICE_TLV);
        assertEquals("CON GET MID:0 URI:/test ContTp:200 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164", cp.toString(true));

        cp.headers().setContentFormat(MediaTypes.CT_APPLICATION_EXI);
        assertEquals("CON GET MID:0 URI:/test ContTp:47 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164", cp.toString(true));

        //unknown
        cp.headers().setContentFormat((short) 5321);
        assertEquals("CON GET MID:0 URI:/test ContTp:5321 pl(51):0x6c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164206c6f6e67207061796c6f6164", cp.toString(true));
    }
}
