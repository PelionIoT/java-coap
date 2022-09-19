/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.packet.Opaque.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.transport.TransportContext;
import org.junit.jupiter.api.Test;

class CoapTcpPacketConverterTest {

    @Test
    void convertRequestToCoap() {
        CoapRequest req = CoapRequest.post(LOCAL_5683, "/path1")
                .token(123)
                .payload("<test>", MediaTypes.CT_APPLICATION_XML);

        // when
        CoapPacket coapPacket = CoapTcpPacketConverter.toCoapPacket(req);

        // then
        CoapPacket expected = new CoapPacket(LOCAL_5683);
        expected.setMessageType(null);
        expected.setToken(variableUInt(123));
        expected.setMethod(Method.POST);
        expected.headers().setUriPath("/path1");
        expected.headers().setContentFormat(MediaTypes.CT_APPLICATION_XML);
        expected.setPayload("<test>");

        assertEquals(expected, coapPacket);
    }

    @Test
    void convertPingToCoap() {
        CoapRequest req = CoapRequest.ping(LOCAL_5683, TransportContext.EMPTY);

        // when
        CoapPacket coapPacket = CoapTcpPacketConverter.toCoapPacket(req);

        // then
        CoapPacket expected = new CoapPacket(LOCAL_5683);
        expected.setCode(Code.C702_PING);
        expected.setMethod(null);
        expected.setMessageType(null);
        expected.setPayload(Opaque.EMPTY);

        assertEquals(expected, coapPacket);
    }

    @Test
    void convertSeperateResponseToCoap() {
        SeparateResponse resp = CoapResponse.of(Code.C201_CREATED, "<test>")
                .maxAge(412)
                .etag(Opaque.of("123"))
                .toSeparate(variableUInt(142), LOCAL_5683, TransportContext.EMPTY);

        // when
        CoapPacket coapPacket = CoapTcpPacketConverter.toCoapPacket(resp);

        // then
        CoapPacket expected = new CoapPacket(LOCAL_5683);
        expected.setMessageType(null);
        expected.setToken(variableUInt(142));
        expected.setCode(Code.C201_CREATED);
        expected.headers().setMaxAge(412L);
        expected.headers().setEtag(Opaque.of("123"));
        expected.setPayload("<test>");

        assertEquals(expected, coapPacket);
    }
}