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
package com.mbed.coap.packet;

import static com.mbed.coap.packet.BasicHeaderOptions.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapMessageFormatException;
import com.mbed.coap.exception.CoapUnknownOptionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


public class HeaderOptionTest {

    @Test
    public void testEmpty() throws IOException, CoapException {
        BasicHeaderOptions hdr = new BasicHeaderOptions();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(0, baos.size());
    }

    @Test
    public void headerWithLargeDelta() throws IOException, CoapException {
        BasicHeaderOptions hdr = new BasicHeaderOptions();
        hdr.setProxyUri("/testuri"); //35

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        byte[] expected = new byte[]{(byte) 0xD8, 0x16, '/', 't', 'e', 's', 't', 'u', 'r', 'i'};
        assertArrayEquals(expected, baos.toByteArray());
        BasicHeaderOptions hdr2 = new BasicHeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(expected), null);
        assertEquals(hdr.getProxyUri(), hdr2.getProxyUri());
        assertEquals(hdr.getProxyScheme(), hdr2.getProxyScheme());

        //larger delta
        hdr = new BasicHeaderOptions();
        hdr.put(300, Opaque.of("test"), null);

        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        expected = new byte[]{(byte) 0xE4, 0x00, 0x1F, 't', 'e', 's', 't'};
        assertArrayEquals(expected, baos.toByteArray());
    }

    @Test
    public void testMultipleHeaders() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setUriPath("/test/uri/path");
        //hdr.setToken(HeaderOptions.convertVariableUInt(123456));
        hdr.setContentFormat((short) 1);
        hdr.setEtag(Opaque.variableUInt((56789)));
        hdr.setLocationPath("/location/path");
        hdr.setProxyUri("/proxy/uri");
        hdr.setProxyScheme("coap");
        hdr.setUriHost("uri-host");
        hdr.setUriPort(5683);
        hdr.setUriQuery("par1=dupa&par2=dupa2");
        hdr.put(36, Opaque.variableUInt((1357)));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        HeaderOptions hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);

        System.out.println(hdr);
        System.out.println(hdr2);
        assertTrue(hdr.equals(hdr2));

    }

    @Test
    public void testWithEmptyLocation() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        //hdr.setUriPath("/test/uri/path");
        hdr.setLocationPath("");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        HeaderOptions hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);

        System.out.println(hdr);
        System.out.println(hdr2);
        assertTrue(hdr.equals(hdr2));
    }

    @Test
    public void testWithAccept() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setAccept((short) 123);
        HeaderOptions hdr2 = deserialize(serialize(hdr), hdr.getOptionCount());

        System.out.println(hdr.toString());
        System.out.println(hdr2.toString());
        assertEquals(123, hdr2.getAccept().intValue());
        assertEquals(hdr, hdr2);

        hdr.setAccept(null);
        hdr2 = deserialize(serialize(hdr), hdr.getOptionCount());
        assertEquals(hdr, hdr2);
    }

    @Test
    public void should_fail_when_illegal_accept_value() {
        HeaderOptions hdr = new HeaderOptions();

        assertThatThrownBy(() -> hdr.setAccept(-1)).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hdr.setAccept(0x10000)).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testWithPath() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setUriPath("/path2");
        hdr.setLocationPath("");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        HeaderOptions hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);

        System.out.println(hdr);
        System.out.println(hdr2);
        assertTrue(hdr.equals(hdr2));
        assertEquals(1, hdr.getOptionCount());
        assertEquals(1, hdr2.getOptionCount());
    }

    @Test
    public void testMultipleExHeaders() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setBlock1Req(new BlockOption(2, BlockSize.S_16, true));
        hdr.setBlock2Res(new BlockOption(4, BlockSize.S_1024, false));
        hdr.setObserve(4321);

        HeaderOptions hdr2 = deserialize(serialize(hdr), hdr.getOptionCount());

        System.out.println(hdr);
        System.out.println(hdr2);
        assertTrue(hdr.equals(hdr2));

    }

    @Test
    public void testWithLargeOptionNumbers() throws IOException, CoapException {
        BasicHeaderOptions hdr = new BasicHeaderOptions();
        hdr.put(1000, Opaque.variableUInt(123456));
        hdr.put(12000, Opaque.variableUInt(98));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        HeaderOptions hdr2 = new HeaderOptions();
        System.out.println(Arrays.toString(baos.toByteArray()));
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);
    }

    @Test
    public void largeOptionValues() throws IOException, CoapException {
        Opaque OPT_VAL1 = Opaque.of("123456789 1234567890"); //20
        Opaque OPT_VAL2 = Opaque.of("123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "); //300
        Opaque OPT_VAL3 = Opaque.of("123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "); //600
        Opaque OPT_VAL4 = Opaque.of("123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "
                + "123456789 1234567890123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 "); //900

        BasicHeaderOptions hdr = new BasicHeaderOptions();
        hdr.put(101, OPT_VAL1);
        hdr.put(102, OPT_VAL2);
        hdr.put(103, OPT_VAL3);
        hdr.put(104, OPT_VAL4);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);

        BasicHeaderOptions hdr2 = new BasicHeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);
        System.out.println(hdr);
        System.out.println(hdr2);
        assertEquals(hdr, hdr2);

        assertEquals(OPT_VAL1, hdr2.getCustomOption(101));
        assertEquals(OPT_VAL2, hdr2.getCustomOption(102));
        assertEquals(OPT_VAL3, hdr2.getCustomOption(103));
        assertEquals(OPT_VAL4, hdr2.getCustomOption(104));
        assertNull(hdr2.getCustomOption(999));
    }


    @Test
    public void specialHeaderValueSizes() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setUriPath("/123456789012"); //12

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(1 + 12, baos.size());
        assertEquals((byte) 0xBC, baos.toByteArray()[0]);    //header
        assertEquals((byte) '1', baos.toByteArray()[1]);     //first byte of option value

        hdr.setUriPath("/1234567890123"); //13

        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(2 + 13, baos.size());
        assertEquals((byte) 0xBD, baos.toByteArray()[0]);    //header
        assertEquals((byte) 0x00, baos.toByteArray()[1]);    //extended len
        assertEquals((byte) '1', baos.toByteArray()[2]);     //first byte of option value

        hdr.setUriPath("/1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "12345678901234567890123456789012345678901234567890123456789012345678"); //268

        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(2 + 268, baos.size());
        assertEquals((byte) 0xBD, baos.toByteArray()[0]);    //header
        assertEquals((byte) 0xFF, baos.toByteArray()[1]);    //extended len
        assertEquals((byte) '1', baos.toByteArray()[2]);     //first byte of option value

        hdr.setUriPath("/1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890123456789012345678901234567890123456789"); //269

        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(3 + 269, baos.size());
        assertEquals((byte) 0xBE, baos.toByteArray()[0]);    //header
        assertEquals((byte) 0x00, baos.toByteArray()[1]);    //extended len
        assertEquals((byte) 0x00, baos.toByteArray()[2]);    //extended len
        assertEquals((byte) '1', baos.toByteArray()[3]);     //first byte of option value
    }

    @Test
    public void testWithLargeOptionAmount() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setUriPath("/1/2/3");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(6, baos.size());

        hdr.setUriPath("/1/2/3/4/5/6/7/8/9/0/1/2/3/4");
        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(2 * 14, baos.size());
        HeaderOptions hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);
        assertEquals(hdr, hdr2);

        hdr.setUriPath("/1/2/3/4/5/6/7/8/9/0/1/2/3/4/5");
        baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        assertEquals(2 * 15, baos.size());
        hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(baos.toByteArray()), null);
        assertEquals(hdr, hdr2);

    }

    @Test
    public void testEaquals() {
        BasicHeaderOptions hdr1 = new BasicHeaderOptions();
        hdr1.setContentFormat((short) 1);
        hdr1.setUriPath("/test/uri");

        BasicHeaderOptions hdr2 = new BasicHeaderOptions();
        hdr2.setContentFormat((short) 1);
        hdr2.setUriPath("/test/uri");

        assertTrue(hdr1.equals(hdr2));
        assertEquals(hdr1.hashCode(), hdr2.hashCode());

        HeaderOptions hdr3 = new HeaderOptions();
        hdr3.setContentFormat((short) 1);
        hdr3.setUriPath("/test/uri3");

        HeaderOptions hdr4 = new HeaderOptions();
        hdr4.setContentFormat((short) 1);
        hdr4.setUriPath("/test/uri");

        assertFalse(hdr3.equals(hdr4));
        assertNotEquals(hdr3.hashCode(), hdr4.hashCode());

        hdr3.setUriPath("/test/uri");
        hdr3.setObserve(1234);
        hdr4.setObserve(4321);
        assertFalse(hdr3.equals(hdr4));

        hdr3.setObserve(1234);
        hdr4.setObserve(1234);
        assertTrue(hdr3.equals(hdr4));
    }

    @Test
    public void testIllegalLocationPath() {
        BasicHeaderOptions hdr = new BasicHeaderOptions();
        assertThrows(IllegalArgumentException.class, () ->
                hdr.setLocationPath(".")
        );
    }

    @Test
    public void testSizeOption() throws IOException, CoapException {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setSize1(3211);

        HeaderOptions hdr2 = deserialize(serialize(hdr), hdr.getOptionCount());
        assertEquals(hdr, hdr2);
        assertEquals((Integer) 3211, hdr2.getSize1());

        hdr2.setSize1(3212);
        assertFalse(hdr.equals(hdr2));
        hdr.setSize1(null);
        assertFalse(hdr.equals(hdr2));
        hdr2.setSize1(null);
        assertTrue(hdr.equals(hdr2));
    }

    @Test
    public void malformedHeaderWithIllegalDelta() throws IOException, CoapMessageFormatException {
        BasicHeaderOptions hdr = new BasicHeaderOptions();
        assertThrows(CoapMessageFormatException.class, () ->
                hdr.deserialize(new ByteArrayInputStream(new byte[]{(byte) 0xF3}), null)
        );
    }

    @Test
    public void split() {
        assertArrayEquals(new String[]{"", "3", "", ""}, DataConvertingUtility.split("/3//", '/'));
        assertArrayEquals(new String[]{"", "3", "", "7"}, DataConvertingUtility.split("/3//7", '/'));
        assertArrayEquals(new String[]{"", "3", "20", "7"}, DataConvertingUtility.split("/3/20/7", '/'));

        assertArrayEquals("/1/2/3".split("/"), DataConvertingUtility.split("/1/2/3", '/'));
        assertArrayEquals("/1//3".split("/"), DataConvertingUtility.split("/1//3", '/'));
        assertArrayEquals("/1/432fsdfs/3fds".split("/"), DataConvertingUtility.split("/1/432fsdfs/3fds", '/'));
        assertArrayEquals("boo:and:foo".split("x"), DataConvertingUtility.split("boo:and:foo", 'x'));

    }

    @Test
    public void uriPath_withMultipleEmptyPathSegments() throws Exception {
        HeaderOptions hdr = new HeaderOptions();
        hdr.setUriPath("/3//");
        HeaderOptions hdr2 = deserialize(serialize(hdr), (byte) 0);
        assertEquals(hdr, hdr2);
        assertEquals("/3//", hdr2.getUriPath());
    }

    @Test
    public void criticalOptTest() throws Exception {
        BasicHeaderOptions h = new BasicHeaderOptions();
        h.criticalOptTest();

        h.put(1000, Opaque.of("foo"));
        h.criticalOptTest();

        h.put(1001, Opaque.of("foo"));
        assertThatThrownBy(h::criticalOptTest).isExactlyInstanceOf(CoapUnknownOptionException.class);
    }

    @Test
    public void optionCharacteristics() throws Exception {
        assertTrue(isCritical(HeaderOptions.IF_MATCH));
        assertFalse(isUnsave(HeaderOptions.IF_MATCH));
        assertFalse(hasNoCacheKey(HeaderOptions.IF_MATCH));

        assertFalse(isCritical(HeaderOptions.ETAG));
        assertFalse(isUnsave(HeaderOptions.ETAG));
        assertFalse(hasNoCacheKey(HeaderOptions.ETAG));

        assertTrue(isCritical(HeaderOptions.URI_PORT));
        assertTrue(isUnsave(HeaderOptions.URI_PORT));
        assertFalse(hasNoCacheKey(HeaderOptions.URI_PORT));

        assertFalse(isCritical(HeaderOptions.SIZE1));
        assertFalse(isUnsave(HeaderOptions.SIZE1));
        assertTrue(hasNoCacheKey(HeaderOptions.SIZE1));
    }

    @Test
    public void settingValuesOverRange() throws Exception {
        HeaderOptions h = new HeaderOptions();

        h.setMaxAge(null);
        assertNull(h.getMaxAge());

        h.setMaxAge(0x1FFFFFFFFL);
        assertEquals(0xFFFFFFFFL, h.getMaxAgeValue());

        assertThatThrownBy(() -> h.setEtag(Opaque.of("123456789"))).isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> h.setEtag(new Opaque[]{Opaque.of("123456789")})).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.setEtag(new Opaque[]{Opaque.EMPTY})).isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> h.setLocationPath(".")).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.setLocationPath("..")).isExactlyInstanceOf(IllegalArgumentException.class);

        h.setLocationPath("");
        assertNull(h.getLocationPath());

        assertThatThrownBy(() -> h.setUriPath("no-leading-slash")).isExactlyInstanceOf(IllegalArgumentException.class);

        h.setUriQuery("");
        assertNull(h.getUriQuery());
    }

    @Test
    public void failWhenTooLargeToSerialize() throws Exception {
        HeaderOptions h = new HeaderOptions();
        h.put(100, new Opaque(new byte[65805]));
        assertThrows(IllegalArgumentException.class, () ->
                h.serialize(Mockito.mock(OutputStream.class))
        );
    }

    @Test
    public void failWhenTooLargeDeltaToSerialize() throws Exception {
        HeaderOptions h = new HeaderOptions();
        h.setIfNonMatch(false);
        h.put(65805, new Opaque(new byte[1]));
        assertThrows(IllegalArgumentException.class, () ->
                h.serialize(Mockito.mock(OutputStream.class))
        );
    }

    @Test
    public void failToDeserializeWithMalformedData() throws Exception {

        assertThatThrownBy(() -> new HeaderOptions().deserialize(new ByteArrayInputStream(new byte[]{(byte) 0xf2}), null))
                .isExactlyInstanceOf(CoapMessageFormatException.class);

        assertThatThrownBy(() -> new HeaderOptions().deserialize(new ByteArrayInputStream(new byte[]{0x3f}), null))
                .isExactlyInstanceOf(CoapMessageFormatException.class);

    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(HeaderOptions.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();

        assertFalse(new BasicHeaderOptions().equals(null));
    }

    private static byte[] serialize(BasicHeaderOptions hdr) throws IOException, CoapException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        hdr.serialize(baos);
        return baos.toByteArray();
    }

    private static HeaderOptions deserialize(byte[] rawData, byte optNumber) throws IOException, CoapException {
        HeaderOptions hdr2 = new HeaderOptions();
        hdr2.deserialize(new ByteArrayInputStream(rawData), null);
        return hdr2;
    }
}
