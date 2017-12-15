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
package com.mbed.coap.server.internal;

import static com.mbed.coap.server.internal.CoapTcpCSM.*;
import static org.junit.Assert.*;
import com.mbed.coap.packet.BlockSize;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class CoapTcpCSMTest {

    @Test
    public void blockSize() {
        assertEquals(null, new CoapTcpCSM(20, false).getBlockSize());
        assertEquals(null, new CoapTcpCSM(10242, false).getBlockSize());

        assertEquals(BlockSize.S_16, new CoapTcpCSM(16, true).getBlockSize());
        assertEquals(BlockSize.S_32, new CoapTcpCSM(32, true).getBlockSize());
        assertEquals(BlockSize.S_64, new CoapTcpCSM(64, true).getBlockSize());
        assertEquals(BlockSize.S_128, new CoapTcpCSM(128, true).getBlockSize());
        assertEquals(BlockSize.S_256, new CoapTcpCSM(256, true).getBlockSize());
        assertEquals(BlockSize.S_512, new CoapTcpCSM(512, true).getBlockSize());
        assertEquals(BlockSize.S_512, new CoapTcpCSM(1023, true).getBlockSize());
        assertEquals(BlockSize.S_1024, new CoapTcpCSM(1024, true).getBlockSize());
        assertEquals(BlockSize.S_1024, new CoapTcpCSM(1025, true).getBlockSize());

        assertEquals(BlockSize.S_1024_BERT, new CoapTcpCSM(1153, true).getBlockSize());
    }

    @Test
    public void min() {
        assertEquals(new CoapTcpCSM(16, true), CoapTcpCSM.min(new CoapTcpCSM(16, true), new CoapTcpCSM(16, true)));

        assertEquals(new CoapTcpCSM(16, false), CoapTcpCSM.min(new CoapTcpCSM(20, false), new CoapTcpCSM(16, true)));
        assertEquals(new CoapTcpCSM(10, false), CoapTcpCSM.min(new CoapTcpCSM(10, true), new CoapTcpCSM(16, false)));
        assertEquals(new CoapTcpCSM(16, true), CoapTcpCSM.min(new CoapTcpCSM(20, true), new CoapTcpCSM(16, true)));
    }

    @Test
    public void withNewOptions() {
        assertEquals(new CoapTcpCSM(1152, false), BASE.withNewOptions(null, null));
        assertEquals(new CoapTcpCSM(1000, false), BASE.withNewOptions(1000L, null));
        assertEquals(new CoapTcpCSM(1000, true), BASE.withNewOptions(1000L, true));
    }

    @Test
    public void should_return_maxOutboundPayloadSize() {
        assertEquals(2000, new CoapTcpCSM(2000, false).getMaxOutboundPayloadSize());

        assertEquals(16, new CoapTcpCSM(16, true).getMaxOutboundPayloadSize());
        assertEquals(512, new CoapTcpCSM(600, true).getMaxOutboundPayloadSize());
        assertEquals(1024, new CoapTcpCSM(1200, true).getMaxOutboundPayloadSize());

        assertEquals(1024, new CoapTcpCSM(2000, true).getMaxOutboundPayloadSize());
        assertEquals(1024, new CoapTcpCSM(3000, true).getMaxOutboundPayloadSize());
        assertEquals(2048, new CoapTcpCSM(4000, true).getMaxOutboundPayloadSize());
        assertEquals(3072, new CoapTcpCSM(5000, true).getMaxOutboundPayloadSize());
        assertEquals(4096, new CoapTcpCSM(6000, true).getMaxOutboundPayloadSize());
    }


    @Test
    public void should_determine_to_use_block_transfer() {
        CoapTcpCSM csm = new CoapTcpCSM(512, false);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(new byte[10]));

        //BLOCK
        csm = new CoapTcpCSM(512, true);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(new byte[10]));
        assertTrue(csm.useBlockTransfer(new byte[513]));

        //BERT
        csm = new CoapTcpCSM(3000, true);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(new byte[10]));
        assertTrue(csm.useBlockTransfer(new byte[3000]));
    }


    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(CoapTcpCSM.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }

}