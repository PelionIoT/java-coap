/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.transport.javassl;

import static com.mbed.coap.utils.HexArray.*;
import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;

public class CoapShimSerializerTest {

    CoapShimSerializer shimSerializer = new CoapShimSerializer(10_000);

    @Test
    public void shimSerializer() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        shimSerializer.serialize(baos, newCoapPacket(0).ack(Code.C205_CONTENT).build());

        assertEquals(8, baos.toByteArray().length);
        assertEquals("00000004", toHex(baos.toByteArray(), 4));
    }

    @Test
    public void shimSerializer_large_payload() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        shimSerializer.serialize(baos, newCoapPacket(0).ack(Code.C205_CONTENT).payload(new byte[1640]).build());

        assertEquals(1649, baos.toByteArray().length);
        assertEquals("0000066d", toHex(baos.toByteArray(), 4));
    }

    @Test
    public void deserialize() throws CoapException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(fromHex("00000004" + "60450000" + "ffff"));

        CoapPacket coapPacket = shimSerializer.deserialize(bais, null);

        assertEquals(newCoapPacket(0).ack(Code.C205_CONTENT).build(), coapPacket);
    }

    @Test(expected = IOException.class)
    public void fail_deserialize_when_too_large() throws CoapException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(fromHex("01020304" + "ffffffff"));

        shimSerializer.deserialize(bais, null);
    }
}
