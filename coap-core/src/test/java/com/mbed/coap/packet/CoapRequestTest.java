/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.packet;

import static com.mbed.coap.packet.CoapRequest.ping;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.transport.TransportContext;
import java.net.InetSocketAddress;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

class CoapRequestTest {
    private InetSocketAddress destination = new InetSocketAddress("localhost", 5683);

    @Test
    public void buildWithAllPossibleFields() {
        CoapRequest buildRequest = CoapRequest.get("/0/1/2")
                .address(destination)
                .accept((short) 1)
                .blockSize(BlockSize.S_16)
                .etag(Opaque.ofBytes(10, 8, 6))
                .host("some.com")
                .ifMatch(Opaque.ofBytes(9, 7, 5))
                .maxAge(789456L)
                .payload("perse", MediaTypes.CT_TEXT_PLAIN)
                .query("p=1")
                .query("b", "2")
                .token(45463L);

        CoapRequest expected = new CoapRequest(Method.GET, Opaque.ofBytes(0xB1, 0x97), new HeaderOptions(), Opaque.of("perse"), destination, TransportContext.EMPTY);
        expected.options().setUriPath("/0/1/2");
        expected.options().setAccept((short) 1);
        expected.options().setEtag(Opaque.ofBytes(10, 8, 6));
        expected.options().setUriHost("some.com");
        expected.options().setIfMatch(new Opaque[]{Opaque.ofBytes(9, 7, 5)});
        expected.options().setMaxAge(789456L);
        expected.options().setUriQuery("p=1&b=2");
        expected.options().setContentFormat(MediaTypes.CT_TEXT_PLAIN);
        expected.options().setBlock2Res(new BlockOption(0, BlockSize.S_16, false));

        assertEquals(expected, buildRequest);
    }


    @Test
    public void malformedUriQuery() {
        CoapRequest req = CoapRequest.put("/0/1/2");
        failQueryWithNonValidChars(req, "", "2");
        failQueryWithNonValidChars(req, "&", "2");
        failQueryWithNonValidChars(req, "=", "54");
        failQueryWithNonValidChars(req, "f", "");
        failQueryWithNonValidChars(req, "f", "&");
        failQueryWithNonValidChars(req, "f", "=");
    }

    private static void failQueryWithNonValidChars(CoapRequest req, String name, String val) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> req.query(name, val));

        assertEquals("Non valid characters provided in query", e.getMessage());
    }

    @Test
    void shouldCreatePing() {
        CoapRequest ping = ping(destination, TransportContext.EMPTY);

        assertTrue(ping.isPing());
        assertThrows(NullPointerException.class, () -> ping.payload("a").isPing());
        assertThrows(NullPointerException.class, () -> ping.token(1).isPing());

        assertFalse(new CoapRequest(Method.GET, Opaque.EMPTY, new HeaderOptions(), Opaque.EMPTY, destination, TransportContext.EMPTY).isPing());
    }

    @Test
    void testToString() {
        assertEquals("CoapRequest[PUT URI:/test,Token:03ff, pl(4):64757061]", CoapRequest.put("/test").token(1023).payload("dupa").toString());
        assertEquals("CoapRequest[POST URI:/test, pl(4):64757061]", CoapRequest.post("/test").payload("dupa").toString());
        assertEquals("CoapRequest[DELETE URI:/test,Token:03ff]", CoapRequest.delete("/test").token(1023).toString());
        assertEquals("CoapRequest[GET URI:/test]", CoapRequest.get("/test").toString());
        assertEquals("CoapRequest[FETCH URI:/test, pl(4):64757061]", CoapRequest.fetch("/test").payload("dupa").toString());
        assertEquals("CoapRequest[PATCH URI:/test, pl(4):64757061]", CoapRequest.patch("/test").payload("dupa").toString());
        assertEquals("CoapRequest[iPATCH URI:/test, pl(4):64757061]", CoapRequest.iPatch("/test").payload("dupa").toString());
        assertEquals("CoapRequest[PING]", CoapRequest.ping(destination, TransportContext.EMPTY).toString());
    }

    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(CoapRequest.class).suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .withPrefabValues(TransportContext.class, TransportContext.EMPTY, TransportContext.of(TransportContext.NON_CONFIRMABLE, true))
                .verify();
    }
}
