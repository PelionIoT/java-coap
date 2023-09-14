/**
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
package com.mbed.lwm2m.tlv;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import com.mbed.lwm2m.LWM2MID;
import com.mbed.lwm2m.LWM2MObjectInstance;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.Test;

public class TLVSerializerTest {

    @Test
    public void serializeStringWithLengthOf3() {
        LWM2MResource manufacturer = new LWM2MResource(LWM2MID.from(0), "ARM");
        byte[] manufacturerTLV = TLVSerializer.serialize(manufacturer);

        assertThat(manufacturerTLV.length, equalTo(5) );
        assertArrayEquals( new byte[] {
                (byte) 0b11_0_00_011,
                (byte) 0,
                'A', 'R', 'M',
                },
        manufacturerTLV);
    }

    @Test
    public void serializeStringWithLengthOf13() {
        LWM2MResource model = new LWM2MResource(LWM2MID.from(1), "nanoservice 2");
        byte[] modelTLV = TLVSerializer.serialize(model);

        assertThat(modelTLV.length, equalTo(16) );
        assertArrayEquals( new byte[] {
                (byte) 0b11_0_01_000,
                (byte) 1,
                (byte) 13,
                'n', 'a', 'n', 'o', 's', 'e', 'r', 'v', 'i', 'c', 'e', ' ', '2',
                },
                modelTLV);
    }

    @Test
    public void serializeStringWithLengthOf256() {
        byte[] name = new byte[256];
        LWM2MResource model = new LWM2MResource(LWM2MID.from(1), name);
        byte[] modelTLV = TLVSerializer.serialize(model);

        assertThat(modelTLV.length, equalTo(260) );
        assertArrayEquals( new byte[] {
                (byte) 0b11_0_10_000,
                (byte) 1,
                (byte) 0x01,
                (byte) 0x00,
                },
                Arrays.copyOf(modelTLV, 4));
    }

    @Test
    public void serializeStringWithLengthOf65536() {
        byte[] name = new byte[65536];
        LWM2MResource model = new LWM2MResource(LWM2MID.from(1), name);
        byte[] modelTLV = TLVSerializer.serialize(model);

        assertThat(modelTLV.length, equalTo(65541) );
        assertArrayEquals( new byte[] {
                (byte) 0b11_0_11_000,
                (byte) 1,
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0x00,
                },
                Arrays.copyOf(modelTLV, 5));
    }

    @Test
    public void serializeWithLongID() throws Exception {
        LWM2MResource manufacturer = new LWM2MResource(LWM2MID.from(256), "ARM");
        byte[] manufacturerTLV = TLVSerializer.serialize(manufacturer);

        assertThat(manufacturerTLV.length, equalTo(6) );
        assertArrayEquals( new byte[] {
                (byte) 0b11_1_00_011,
                (byte) 1,
                (byte) 0,
                'A', 'R', 'M',
                },
        manufacturerTLV);
    }

    @Test
    public void serializeTwoResources() throws Exception {
        LWM2MResource manufacturer = new LWM2MResource(LWM2MID.from(0), "ARM");
        LWM2MResource model = new LWM2MResource(LWM2MID.from(1), "nanoservice 2");

        byte[] tlv = TLVSerializer.serializeResources(Arrays.asList(manufacturer, model));
        assertEquals (21, tlv.length);
        assertArrayEquals( new byte[] {
                (byte) 0b11_0_00_011,
                (byte) 0,
                'A', 'R', 'M',
                (byte) 0b11_0_01_000,
                (byte) 1,
                (byte) 13,
                'n', 'a', 'n', 'o', 's', 'e', 'r', 'v', 'i', 'c', 'e', ' ', '2'
                },
                tlv);
    }

    @Test
    public void serializeMultipleResource() throws Exception {
        LWM2MResourceInstance internalBattery = new LWM2MResourceInstance(LWM2MID.create(), 0x01);
        LWM2MResourceInstance usbBattery = new LWM2MResourceInstance(LWM2MID.create(), 0x05);
        LWM2MResource power = new LWM2MResource(LWM2MID.from(6), Arrays.asList(internalBattery, usbBattery));

        byte[] powerTLV = TLVSerializer.serialize(power);
        assertEquals(8, powerTLV.length);
        assertArrayEquals( new byte[] {
                (byte) 0b10_0_00_110,
                (byte) 6,
                (byte) 0b01_0_00_001,
                (byte) 0,
                (byte) 0x01,
                (byte) 0b01_0_00_001,
                (byte) 1,
                (byte) 0x05,
                },
                powerTLV);
    }

    @Test
    public void serializeMultipleObjectInstances() throws Exception {
        // See LWM2M spec Chapter 6.3.3.2

        // GET /2
        LWM2MObjectInstance aco0 = new LWM2MObjectInstance(LWM2MID.$0, Arrays.asList(
            new LWM2MResource(LWM2MID.from(0), 0x03),                       // resource: Object ID
            new LWM2MResource(LWM2MID.from(1), 0x01),                       // resource: Object Instance ID
            new LWM2MResource(LWM2MID.from(2), Arrays.asList(               // multiple resource: ACL
                new LWM2MResourceInstance(LWM2MID.from(1), 0b11_10_0000),   // resource instance: ACL [1]
                new LWM2MResourceInstance(LWM2MID.from(2), 0b10_00_0000))   // resource instance: ACL [1]
            ),
            new LWM2MResource(LWM2MID.from(3), 0x01)                        // resource: Access Control Owner
        ));
        LWM2MObjectInstance aco1 = new LWM2MObjectInstance(LWM2MID.$1, Arrays.asList(
                new LWM2MResource(LWM2MID.from(0), 0x04),
                new LWM2MResource(LWM2MID.from(1), 0x02),
                new LWM2MResource(LWM2MID.from(2), Arrays.asList(
                    new LWM2MResourceInstance(LWM2MID.from(1), 0b10_00_0000),
                    new LWM2MResourceInstance(LWM2MID.from(2), 0b10_00_0000))
                ),
                new LWM2MResource(LWM2MID.from(3), 0x01)
            ));

        byte[] acoTLV = TLVSerializer.serialize(aco0, aco1);
        assertEquals(40, acoTLV.length);
    }

    @Test
    public void serializeCertificates() throws Exception {
        LWM2MResource secMode = new LWM2MResource(LWM2MID.from(2), 2);
        LWM2MResource cert = new LWM2MResource(LWM2MID.from(4), Base64.getDecoder().decode("MIIBKjCB0aADAgECAgEBMAoGCCqGSM49BAMCMC4xETAPBgNVBAMMCEFSTS1URVNUMQwwCgYDVQQKDANBUk0xCzAJBgNVBAYTAkZJMB4XDTE0MDQwMzA4MTgxOFoXDTE2MDQwMjA4MTgxOFowEDEOMAwGA1UEAwwFTlNQLTEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASzoo3RqwjZEWJdqeQO8MTtGYlG/jZrxodAe4FC6Yu+32ahNakfJDMSEnLAQrRvndT8FGDL6eapppR1nUrR0LgTMAoGCCqGSM49BAMCA0gAMEUCIQCthoYZgRd4YiT5VzahEd7yIFhD25DODjOlI4En6YYHogIgC5vqLucdr6A0HGZlr38VddGw5Vg2ntD8dvbEyXBIrlg="));
        LWM2MResource priv = new LWM2MResource(LWM2MID.from(5), Base64.getDecoder().decode("MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgeDict5S0Dz1OCthRTPIxWdidQGezkscQluPiKt7zD/uhRANCAASzoo3RqwjZEWJdqeQO8MTtGYlG/jZrxodAe4FC6Yu+32ahNakfJDMSEnLAQrRvndT8FGDL6eapppR1nUrR0LgT"));
        LWM2MResource url = new LWM2MResource(LWM2MID.from(0), "coaps://localhost:64464");
        LWM2MObjectInstance security = new LWM2MObjectInstance(LWM2MID.from(0), secMode, cert, priv, url);

        byte[] tlv = TLVSerializer.serialize(security);
        List<LWM2MObjectInstance> decoded = TLVDeserializer.deserialiseObjectInstances(tlv);
        assertThat (decoded, hasSize(1));
        List<LWM2MResource> resources = decoded.get(0).getResources();
        assertThat (resources, hasSize(4));
        assertThat (resources.get(0).getId().intValue(), equalTo(2));
        assertThat (resources.get(1).getId().intValue(), equalTo(4));
        assertThat (resources.get(2).getId().intValue(), equalTo(5));
        assertThat (resources.get(3).getId().intValue(), equalTo(0));
        assertThat (resources.get(1).getValue().length, equalTo(302) );
        assertThat (resources.get(2).getValue().length, equalTo(138) );
    }

}
