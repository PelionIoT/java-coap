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
package com.mbed.coap.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class CoapRequestTargetTest {
    private InetSocketAddress destination = new InetSocketAddress("localhost", 5683);
    private CoapClient mockClient = when(mock(CoapClient.class).getDestination()).thenReturn(destination).getMock();

    @Test
    public void test() {
        CoapRequestTarget req = new CoapRequestTarget("/0/1/2", mockClient);

        req.accept((short) 1);
        req.blockSize(BlockSize.S_16);
        req.etag(Opaque.variableUInt(0x100806L));
        req.host("arm.com");
        req.ifMatch(Opaque.variableUInt(0x090705));
        req.ifNotMatch(false);
        req.maxAge(789456L);
        req.non();
        req.payload(Opaque.of("perse"), MediaTypes.CT_TEXT_PLAIN);
        req.query("p=1");
        req.query("b", "2");
        req.token(45463L);

        CoapPacket packet = new CoapPacket(Method.GET, MessageType.NonConfirmable, "/0/1/2", destination);
        packet.headers().setAccept((short) 1);
        packet.headers().setEtag(Opaque.variableUInt(0x100806L));
        packet.headers().setUriHost("arm.com");
        packet.headers().setIfMatch(new Opaque[]{Opaque.variableUInt(0x090705)});
        packet.headers().setIfNonMatch(Boolean.FALSE);
        packet.headers().setMaxAge(789456L);
        packet.headers().setUriQuery("p=1&b=2");
        packet.headers().setContentFormat(MediaTypes.CT_TEXT_PLAIN);
        packet.setToken(Opaque.decodeHex("b197"));
        packet.setPayload("perse");

        assertEquals(packet, req.getRequestPacket());
        //
        req.ifNotMatch();
        req.con();

        packet.setMessageType(MessageType.Confirmable);
        packet.headers().setIfNonMatch(Boolean.TRUE);

        assertEquals(packet, req.getRequestPacket());
    }

    @Test
    public void test2() throws Exception {
        CoapRequestTarget req = new CoapRequestTarget("/0/1/2", mockClient);

        req.payload(Opaque.of("abc"));
        req.contentFormat(MediaTypes.CT_APPLICATION_XML);

        CoapPacket packet = new CoapPacket(Method.GET, MessageType.Confirmable, "/0/1/2", destination);
        packet.headers().setContentFormat(MediaTypes.CT_APPLICATION_XML);
        packet.setPayload("abc");

        assertEquals(packet, req.getRequestPacket());
    }

    @Test
    public void malformedUriQuery() {
        CoapRequestTarget req = new CoapRequestTarget("/0/1/2", mock(CoapClient.class));
        failQueryWithNonValidChars(req, "", "2");
        failQueryWithNonValidChars(req, "&", "2");
        failQueryWithNonValidChars(req, "=", "54");
        failQueryWithNonValidChars(req, "f", "");
        failQueryWithNonValidChars(req, "f", "&");
        failQueryWithNonValidChars(req, "f", "=");

    }

    private static void failQueryWithNonValidChars(CoapRequestTarget req, String name, String val) {
        try {
            req.query(name, val);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Non valid characters provided in query", e.getMessage());
        }
    }
}
