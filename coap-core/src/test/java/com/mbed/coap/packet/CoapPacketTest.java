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
package com.mbed.coap.packet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.utils.HexArray;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

/**
 * @author szymon
 */
public class CoapPacketTest extends CoapPacketTestBase {

    @Test
    public void linkFormat() throws ParseException {

        String linkFormatString = "</a/relay>;if=\"ns.wadl#a\";rt=\"ns:relay\";ct=\"0\"";
        LinkFormat[] lf = LinkFormatBuilder.parseList(linkFormatString);
        assertEquals(1, lf.length);
        assertEquals("/a/relay", lf[0].getUri());
        assertEquals(new String[]{"ns.wadl#a"}, lf[0].getInterfaceDescriptionArray());
        assertEquals(new String[]{"ns:relay"}, lf[0].getResourceTypeArray());
        assertEquals((Short) (short) 0, lf[0].getContentType());

        linkFormatString = "</a/relay>;if=\"ns.wadl#a\";rt=\"ns:relay\";ct=\"0\","
                + "</s/light>;if=\"ns.wadl#s\";rt=\"ucum:lx\";ct=\"0\","
                + "</s/power>;if=\"ns.wadl#s\";rt=\"ucum:W\";ct=\"0\","
                + "</s/temp>;if=\"ns.wadl#s\";rt=\"ucum:Cel\";ct=\"0\"";

        lf = LinkFormatBuilder.parseList(linkFormatString);
        assertEquals(4, lf.length);
    }

    @Test
    public void deserializeAfterSerializeGivesBackACoapPacketWithSameData() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setMessageId(14);
        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertEquals(Method.GET, cp2.getMethod());
        assertEquals(MessageType.Confirmable, cp2.getMessageType());
        assertEquals("/test", cp2.headers().getUriPath());
        assertNull(cp2.getCode());
        assertNull(cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());
    }

    @Test
    public void readSerializedGiveBackSimilarCoapPacket() throws CoapException {
        InetSocketAddress addr = InetSocketAddress.createUnresolved("some.host", 1234);
        CoapPacket cp = new CoapPacket(Code.C205_CONTENT, MessageType.Acknowledgement, addr);
        cp.setPayload("TEST");
        cp.setMessageId(13);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.read(addr, rawCp);

        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertSimilar(cp, cp2);
    }

    @Test
    public void coapPacketTest3() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.PUT, MessageType.Confirmable, "", null);
        cp.setMessageId(1234);
        cp.headers().setUriPath("/test2");
        cp.headers().setLocationPath("");
        cp.setPayload("t�m� on varsin miel??$�");
        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(cp2);
        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertEquals(Method.PUT, cp2.getMethod());
        assertEquals(MessageType.Confirmable, cp2.getMessageType());
        assertEquals("/test2", cp2.headers().getUriPath());
    }

    @Test
    public void coapPacketTestWithHightNumberBlock() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.PUT, MessageType.Reset, "", null);
        cp.headers().setBlock2Res(new BlockOption(0, BlockSize.S_16, true));
        cp.setMessageId(0xFFFF);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));
        System.out.println(cp);
        System.out.println(cp2);

        assertSimilar(cp, cp2);
    }

    @Test
    public void coapPacketTestWithPathAndQuery() throws CoapException, ParseException {
        CoapPacket cp = new CoapPacket(Method.DELETE, MessageType.NonConfirmable, null, null);
        cp.headers().setUriPath("/test/path/1");
        cp.headers().setUriQuery("par1=1&par2=201");
        cp.headers().setLocationPath("/loc/path/2");
        cp.headers().setLocationQuery("lpar1=1&lpar2=2");
        cp.setMessageId(3612);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));
        assertSimilar(cp, cp2);

        Map<String, String> q = new HashMap<>();
        q.put("par1", "1");
        q.put("par2", "201");
        assertEquals(q, cp.headers().getUriQueryMap());

        assertNull(cp.headers().getContentFormat());
        assertNull(cp.headers().getContentFormat());

    }

    @Test
    public void coapPacketTestWithHeaders() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.DELETE, MessageType.NonConfirmable, null, null);
        cp.headers().setAccept(((short) 432));
        cp.headers().setIfMatch(new byte[][]{{(byte) 98, (byte) 53}});
        cp.headers().setIfNonMatch(Boolean.TRUE);
        cp.headers().setContentFormat((short) 423);
        cp.headers().setEtag(new byte[][]{DataConvertingUtility.intToByteArray(98), DataConvertingUtility.intToByteArray(78543)});
        cp.headers().setMaxAge(7118543L);
        cp.headers().setObserve(123);
        cp.headers().setProxyUri("/proxy/uri/test");
        cp.headers().setUriPort(64154);

        cp.setMessageId(3612);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));
        assertSimilar(cp, cp2);
    }

    @Test
    public void coapPacketTestWithEmptyLocHeader() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Reset, "", null);
        cp.headers().setBlock2Res(new BlockOption(0, BlockSize.S_16, true));
        cp.headers().setLocationQuery("");
        cp.setMessageId(0);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));

        assertEquals(Method.GET, cp2.getMethod());
        assertEquals(MessageType.Reset, cp2.getMessageType());
        assertEquals(cp.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(null, cp2.headers().getUriPath());
        assertNull(cp2.getCode());
        assertNull(cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());
    }

    @Test(expected = com.mbed.coap.exception.CoapException.class)
    public void versionTest() throws CoapException {
        CoapPacket.read(null, new byte[]{(byte) 0x85});
    }


    @Test
    public void unknownHeaderTest() throws CoapException {
        CoapPacket cp = new CoapPacket(null);
        cp.setMessageId(0);
        byte[] hdrVal = new byte[]{1, 2, 3, 4, 5, 6, 7};
        int hdrType = 100;
        cp.headers().put(hdrType, hdrVal);
        assertEquals(hdrVal, cp.headers().getCustomOption(hdrType));

        byte[] rawCp = CoapPacket.serialize(cp);

        CoapPacket cp2 = CoapPacket.deserialize(null, new ByteArrayInputStream(rawCp));
        System.out.println(cp);
        System.out.println(cp2);
        //assertEquals(1, cp2.headers().getUnrecognizedOptions().size());
        assertArrayEquals(hdrVal, cp2.headers().getCustomOption(hdrType));
        assertEquals(cp.headers(), cp2.headers());
    }

    @Test
    public void uriPathWithDoubleSlashes() throws CoapException {
        CoapPacket cp = new CoapPacket(null);
        cp.setMessageId(2);
        cp.headers().setUriPath("/3/13/0/");
        cp.headers().setLocationPath("/2//1");
        cp.headers().setUriQuery("te=12&&ble=14");
        cp.toByteArray();
        cp.setMessageId(17);

        CoapPacket cp2 = CoapPacket.read(null, cp.toByteArray());
        assertEquals(cp, cp2);
        assertEquals("/3/13/0/", cp2.headers().getUriPath());
        assertEquals("/2//1", cp2.headers().getLocationPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void messageIdTooBig() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setMessageId(65536);
    }

    @Test
    public void messageIdMaxLimit() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setMessageId(65535);
        assertEquals(65535, cp.getMessageId());
    }

    @Test
    public void messageIdMinLimit() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setMessageId(0);
        assertEquals(0, cp.getMessageId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void messageIdNegative() {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        cp.setMessageId(-1);
    }

    @Test
    public void createResponse() throws Exception {
        //CON 205 MID:321 Token:0x3131
        assertEquals(newCoapPacket(321).con(Code.C205_CONTENT).token(0x3131).build().createResponse(),
                newCoapPacket(321).ack(null).build()); //note, no token!

        //CON GET MID:321 Token:0x3131
        assertEquals(newCoapPacket(321).get().token(0x3131).build().createResponse(),
                newCoapPacket(321).ack(Code.C205_CONTENT).token(0x3131).build());
    }

    @Test
    public void shouldAllowObserveValueUpToThreeBytes() {
        CoapPacket packet = new CoapPacket(null);
        packet.headers().setObserve(0xFFFFFF);

        assertEquals(new Integer(0xFFFFFF), packet.headers().getObserve());

        //non valid
        try {
            packet.headers().setObserve(0xFFFFFF + 1);
            fail();
        } catch (IllegalArgumentException ex) {
            //as expected
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void tokenLimit() throws Exception {
        CoapPacket packet = new CoapPacket(null);
        packet.setToken(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
    }

    @Test(expected = IllegalStateException.class)
    public void failWhenCodeAndMethod() throws Exception {
        CoapPacket packet = new CoapPacket(null);
        packet.setCode(Code.C201_CREATED);
        packet.setMethod(Method.DELETE);

        packet.toByteArray();
    }

    @Test(expected = IllegalStateException.class)
    public void failWhenIOExceptionWhenWriting() throws Exception {
        OutputStream outputStream = mock(OutputStream.class);
        doThrow(new IOException()).when(outputStream).write(any());

        new CoapPacket(null).writeTo(outputStream);
    }

    @Test(expected = CoapException.class)
    public void failWhenIOExceptionWhenReading() throws Exception {
        InputStream inputStream = mock(InputStream.class);
        when(inputStream.read()).thenThrow(new IOException());

        new CoapPacket(null).readFrom(inputStream);
    }

    @Test(expected = CoapException.class)
    public void fail_when_malformed_packet_illegal_observe_argument() throws Exception {
        String malformedPacket = "42021ed720d468bfb0daed7d8da264e29198e37bc4817da81c86b66f2d66276421a4769789b5ab5b1bb09e9300aca1b82362c2759dc6c8d6c87441b012933677704beeab571d3d3d95cfedfc9f57dd0fc145fd8d39af24d7109916a53b52cdb593c19600c15e69088f7405afe2dcbb5fed1ee17b5bd216c7a654f8ab674e24922cd1b57c0c1a626b05e9032cd25e70dbb5383e1b222c930990962c80c4a21ae3148f25d01670aa33444fe4cc6abe904a1b4950c3182e4679242858f8e71ead0d0e5c25f11643c9375f5ec57633cf3fb088d8a4c36c2ecfad042ca33bf4be795fd3b7da341c42ce7b5c70468d8ece307140705285e974a077f0a465785904ecdf0c3a99284fed82c258a490764b1c075dd9fd02577a80f929fcad30395e78b1da4c961f7a39b0c5d5dd8a567bd5abb22df8a3288a2ab9f82aea6e79bfd560baee12294a4611f529096f89d2cffd764463f317b481ba4d7fd6f1d1b40e6dffd6fd0aae724aeb802185f5daaa66645745b46dcf2a09511862b1e852b819527a901bb1662805dc553e2ade9274b41f825d036cbd975ac313f23a9f7d1ec8deb7b2658b915204d6f23e2edf18c782c15a6ce37f67771809a2b298c6270255d0ebe98a69809f75bce7df1b10584604bee1bcdf6758f6210b0cb9163187b4d518d94d84799c3453dd0204b37b214a242e3cb4be522ff0c3b09e96cb37242d4d65779c88590e1438b74a350213346707673c9fd33b39221115e1479d6dd70e787ae2ba612dd40b4ee856e27856ac09f87d0e1bb2d8091e1f6ca343f2f0f8";

        CoapPacket.read(null, HexArray.fromHex(malformedPacket));
    }

    @Test(expected = CoapException.class)
    public void fail_when_malformed_packet_invalid_token() throws Exception {
        String malformedPacket = "4904da1f1b4f54306867554f75b56c61726765ff2584cbed7396e29c7b73b07d173480816dfcf97b08ecb20bb3e3347561c81a3e42afca9e2004ddb905123d3038727599af09bd642647cca94bd09b2daed91bd7096bb1c32244b5052f3349caa9243a2f741f33da320d9142af8d00e662ae673e685df911e5811e352863dd303a3320520c20a26e706f9ffd1ceda579b3a1ca912906d1be2334e1752783d9c927f4bec8cd1c7d9d8095f52db5666b1fbef03b2c1666f183cdc59d5276f8175a8c55bb936663a0e85a1d2d1428bb449ce78447c8700ca1060c61a05330cd5b6daddebe287a3a8aee107da3564d6d26e03c05b8ced83608fbc4363343010b5c67d0e5672ea31d63c9c24061865f9682c50c5f0f0a5ac26c9880b55d6cdbb7e7bc06f551376e21fba5b4ec1c28ff2463a8f572054f09852d18900c6de51b7f";

        CoapPacket.read(null, HexArray.fromHex(malformedPacket));
    }

    @Test(expected = CoapException.class)
    public void fail_when_malformed_packet_invalid_token_2() throws Exception {
        String malformedPacket = "4aab";

        CoapPacket.read(null, HexArray.fromHex(malformedPacket));
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(CoapPacket.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }

    @Test
    public void ignoreIpv6Scope_getRemoteAddrString() throws UnknownHostException {
        byte[] addr = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        CoapPacket coapPacket = new CoapPacket(new InetSocketAddress(Inet6Address.getByAddress(null, addr, 1), 6666));
        assertEquals("102:304:506:708:90a:b0c:d0e:f10:6666", coapPacket.getRemoteAddrString());
    }
}
