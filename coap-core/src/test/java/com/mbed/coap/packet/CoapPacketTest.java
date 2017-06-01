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
package com.mbed.coap.packet;

import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * @author szymon
 */
public class CoapPacketTest {

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
        cp.headers().setAccept(new short[]{});
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
        cp.headers().setAccept(new short[]{12, 432});
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

    private static void assertSimilar(CoapPacket cp1, CoapPacket cp2) {
        assertEquals(cp1.getMethod(), cp2.getMethod());
        assertEquals(cp1.getMessageType(), cp2.getMessageType());
        assertEquals(cp1.getCode(), cp2.getCode());
        assertEquals(cp1.getMessageId(), cp2.getMessageId());

        assertEquals(cp1.headers().getBlock1Req(), cp2.headers().getBlock1Req());
        assertEquals(cp1.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(cp1.headers().getUriPath(), cp2.headers().getUriPath());
        assertEquals(cp1.headers().getUriAuthority(), cp2.headers().getUriAuthority());
        assertEquals(cp1.headers().getUriHost(), cp2.headers().getUriHost());
        assertEquals(cp1.headers().getUriQuery(), cp2.headers().getUriQuery());
        assertEquals(cp1.headers().getLocationPath(), cp2.headers().getLocationPath());
        assertEquals(cp1.headers().getLocationQuery(), cp2.headers().getLocationQuery());

        assertArrayEquals(cp1.headers().getAccept(), cp2.headers().getAccept());
        assertArrayEquals(cp1.headers().getIfMatch(), cp2.headers().getIfMatch());
        assertArrayEquals(cp1.headers().getEtagArray(), cp2.headers().getEtagArray());

        assertEquals(cp1.headers().getIfNonMatch(), cp2.headers().getIfNonMatch());
        assertEquals(cp1.headers().getContentFormat(), cp2.headers().getContentFormat());
        assertArrayEquals(cp1.headers().getEtag(), cp2.headers().getEtag());
        assertEquals(cp1.headers().getMaxAge(), cp2.headers().getMaxAge());
        assertEquals(cp1.headers().getObserve(), cp2.headers().getObserve());
        assertEquals(cp1.headers().getProxyUri(), cp2.headers().getProxyUri());
        assertArrayEquals(cp1.getToken(), cp2.getToken());
        assertEquals(cp1.headers().getUriPort(), cp2.headers().getUriPort());

        assertEquals(cp1.getPayloadString(), cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());

        assertEquals(cp1.getRemoteAddress(), cp2.getRemoteAddress());
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
        cp.headers().setUriPath("/3/13/0/");
        cp.headers().setLocationPath("/2//1");
        cp.headers().setUriQuery("te=12&&ble=14");
        cp.toByteArray();

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
}
