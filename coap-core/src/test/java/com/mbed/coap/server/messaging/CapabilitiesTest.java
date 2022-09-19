/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.server.messaging.Capabilities.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.BlockSize;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class CapabilitiesTest {

    @Test
    public void blockSize() {
        assertEquals(null, new Capabilities(20, false).getBlockSize());
        assertEquals(null, new Capabilities(10242, false).getBlockSize());

        assertEquals(BlockSize.S_16, new Capabilities(16, true).getBlockSize());
        assertEquals(BlockSize.S_32, new Capabilities(32, true).getBlockSize());
        assertEquals(BlockSize.S_64, new Capabilities(64, true).getBlockSize());
        assertEquals(BlockSize.S_128, new Capabilities(128, true).getBlockSize());
        assertEquals(BlockSize.S_256, new Capabilities(256, true).getBlockSize());
        assertEquals(BlockSize.S_512, new Capabilities(512, true).getBlockSize());
        assertEquals(BlockSize.S_512, new Capabilities(1023, true).getBlockSize());
        assertEquals(BlockSize.S_1024, new Capabilities(1024, true).getBlockSize());
        assertEquals(BlockSize.S_1024, new Capabilities(1025, true).getBlockSize());

        assertEquals(BlockSize.S_1024_BERT, new Capabilities(1153, true).getBlockSize());
    }

    @Test
    public void min() {
        assertEquals(new Capabilities(16, true), Capabilities.min(new Capabilities(16, true), new Capabilities(16, true)));

        assertEquals(new Capabilities(16, false), Capabilities.min(new Capabilities(20, false), new Capabilities(16, true)));
        assertEquals(new Capabilities(10, false), Capabilities.min(new Capabilities(10, true), new Capabilities(16, false)));
        assertEquals(new Capabilities(16, true), Capabilities.min(new Capabilities(20, true), new Capabilities(16, true)));
    }

    @Test
    public void withNewOptions() {
        assertEquals(new Capabilities(1152, false), BASE.withNewOptions(null, null));
        assertEquals(new Capabilities(1000, false), BASE.withNewOptions(1000L, null));
        assertEquals(new Capabilities(1000, true), BASE.withNewOptions(1000L, true));
    }

    @Test
    public void should_return_maxOutboundPayloadSize() {
        assertEquals(2000, new Capabilities(2000, false).getMaxOutboundPayloadSize());

        assertEquals(16, new Capabilities(16, true).getMaxOutboundPayloadSize());
        assertEquals(512, new Capabilities(600, true).getMaxOutboundPayloadSize());
        assertEquals(1024, new Capabilities(1200, true).getMaxOutboundPayloadSize());

        assertEquals(1024, new Capabilities(2000, true).getMaxOutboundPayloadSize());
        assertEquals(1024, new Capabilities(3000, true).getMaxOutboundPayloadSize());
        assertEquals(2048, new Capabilities(4000, true).getMaxOutboundPayloadSize());
        assertEquals(3072, new Capabilities(5000, true).getMaxOutboundPayloadSize());
        assertEquals(4096, new Capabilities(6000, true).getMaxOutboundPayloadSize());
    }


    @Test
    public void should_determine_to_use_block_transfer() {
        Capabilities csm = new Capabilities(512, false);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(opaqueOfSize(10)));

        //BLOCK
        csm = new Capabilities(512, true);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(opaqueOfSize(10)));
        assertTrue(csm.useBlockTransfer(opaqueOfSize(513)));

        //BERT
        csm = new Capabilities(3000, true);
        assertFalse(csm.useBlockTransfer(null));
        assertFalse(csm.useBlockTransfer(opaqueOfSize(10)));
        assertTrue(csm.useBlockTransfer(opaqueOfSize(3000)));
    }


    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(Capabilities.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }

}