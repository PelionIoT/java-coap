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

import static com.mbed.coap.server.internal.BlockWiseTransfer.*;
import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import org.junit.Test;


/**
 * Created by szymon
 */
public class BlockWiseTransferTest {
    private CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();
    private BlockWiseTransfer bwt = new BlockWiseTransfer(capabilities);

    @Test
    public void should_set_first_block_header_for_request() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(512, true));
        CoapPacket coap = newCoapPacket(LOCAL_5683).payload(new byte[2000]).post().build();

        assertEquals(1, bwt.updateWithFirstBlock(coap));

        assertEquals(2000, coap.headers().getSize1().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_512, true), coap.headers().getBlock1Req());
        assertNull(coap.headers().getSize2Res());
        assertNull(coap.headers().getBlock2Res());
        assertEquals(512, coap.getPayload().length);
    }

    @Test
    public void should_set_first_block_header_for_request_bert() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(3300, true));
        CoapPacket coap = newCoapPacket(LOCAL_5683).payload(new byte[5000]).post().build();

        assertEquals(2, bwt.updateWithFirstBlock(coap));

        assertEquals(5000, coap.headers().getSize1().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_1024_BERT, true), coap.headers().getBlock1Req());
        assertNull(coap.headers().getSize2Res());
        assertNull(coap.headers().getBlock2Res());
        assertEquals(2048, coap.getPayload().length);
    }

    @Test
    public void should_set_first_block_header_for_observation() {
        capabilities.put(LOCAL_5683, new CoapTcpCSM(512, true));
        CoapPacket coap = newCoapPacket(LOCAL_5683).payload(new byte[2000]).ack(Code.C205_CONTENT).build();

        assertEquals(1, bwt.updateWithFirstBlock(coap));

        assertEquals(2000, coap.headers().getSize2Res().intValue());
        assertEquals(new BlockOption(0, BlockSize.S_512, true), coap.headers().getBlock2Res());
        assertNull(coap.headers().getSize1());
        assertNull(coap.headers().getBlock1Req());
        assertEquals(512, coap.getPayload().length);
    }


    @Test
    public void should_validate_packet_with_block() {
        assertTrue(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[100]).post().build(),
                new BlockOption(1, BlockSize.S_512, false)));

        assertTrue(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[1024]).post().build(),
                new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertTrue(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[512]).post().build(),
                new BlockOption(3, BlockSize.S_512, true)));

        //fail
        assertFalse(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[1023]).post().build(),
                new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertFalse(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[0]).post().build(),
                new BlockOption(2, BlockSize.S_1024_BERT, true)));

        assertFalse(isBlockPacketValid(newCoapPacket(LOCAL_5683).payload(new byte[511]).post().build(),
                new BlockOption(3, BlockSize.S_512, true)));
    }
}