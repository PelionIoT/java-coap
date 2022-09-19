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
package com.mbed.coap.server.block;

import static com.mbed.coap.packet.BlockSize.*;
import static com.mbed.coap.server.block.BlockWiseTransfer.*;
import static com.mbed.coap.utils.Bytes.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.messaging.Capabilities;
import org.junit.jupiter.api.Test;


public class BlockWiseTransferTest {

    @Test
    public void should_set_first_block_header_for_request() {
        Capabilities csm = new Capabilities(512, true);
        CoapRequest req = CoapRequest.post(LOCAL_5683, "/").payload(opaqueOfSize(2000));

        assertEquals(512, BlockWiseTransfer.updateWithFirstBlock(req, csm).size());

        assertEquals(2000, req.options().getSize1().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_512, true), req.options().getBlock1Req());
        assertNull(req.options().getSize2Res());
        assertNull(req.options().getBlock2Res());
    }

    @Test
    public void should_set_first_block_header_for_request_bert() {
        Capabilities csm = new Capabilities(3300, true);
        CoapRequest req = CoapRequest.post(LOCAL_5683, "/").payload(opaqueOfSize(5000));

        assertEquals(2048, BlockWiseTransfer.updateWithFirstBlock(req, csm).size());

        assertEquals(5000, req.options().getSize1().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_1024_BERT, true), req.options().getBlock1Req());
        assertNull(req.options().getSize2Res());
        assertNull(req.options().getBlock2Res());
    }

    @Test
    public void should_set_first_block_header_for_observation() {
        Capabilities csm = new Capabilities(512, true);
        SeparateResponse obs = CoapResponse.ok(opaqueOfSize(2000)).toSeparate(Opaque.EMPTY, null);

        Opaque newPayload = updateWithFirstBlock(obs, csm);

        assertEquals(2000, obs.options().getSize2Res().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_512, true), obs.options().getBlock2Res());
        assertNull(obs.options().getSize1());
        assertNull(obs.options().getBlock1Req());
        assertEquals(512, newPayload.size());
    }


    @Test
    public void should_validate_packet_with_block() {
        assertTrue(isBlockPacketValid(opaqueOfSize(100), new BlockOption(1, BlockSize.S_512, false)));

        assertTrue(isBlockPacketValid(opaqueOfSize(1024), new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertTrue(isBlockPacketValid(opaqueOfSize(512), new BlockOption(3, BlockSize.S_512, true)));

        //fail
        assertFalse(isBlockPacketValid(opaqueOfSize(1023), new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertFalse(isBlockPacketValid(opaqueOfSize(0), new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertFalse(isBlockPacketValid(opaqueOfSize(511), new BlockOption(3, BlockSize.S_512, true)));
    }

    @Test
    void sliceBlockFragment_noBert() {
        Opaque payload = Opaque.of("The Constrained Application Protocol (CoAP)");

        assertEquals(
                Opaque.of("The Constrained "),
                createBlockPart(new BlockOption(0, S_16, false), payload, 10000)
        );

        assertEquals(
                Opaque.of("Application Prot"),
                createBlockPart(new BlockOption(1, S_16, false), payload, 10000)
        );

        assertEquals(
                Opaque.of("ocol (CoAP)"),
                createBlockPart(new BlockOption(2, S_16, false), payload, 10000)
        );
    }

    @Test
    void sliceBlockFragment_bert() {
        Opaque payload = opaqueOfSize('a', 2048).concat(opaqueOfSize('b', 1952));

        assertEquals(
                opaqueOfSize('a', 2048),
                createBlockPart(new BlockOption(0, S_1024_BERT, false), payload, 3000)
        );

        assertEquals(
                opaqueOfSize('b', 1952),
                createBlockPart(new BlockOption(2, S_1024_BERT, false), payload, 3000)
        );

    }
}