package com.mbed.lwm2m.tlv;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import com.arm.mbed.commons.string.Utf8Bytes;
import com.google.common.io.BaseEncoding;
import com.mbed.lwm2m.LWM2MID;
import com.mbed.lwm2m.LWM2MObjectInstance;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.LWM2MResourceInstance;
import com.mbed.lwm2m.utils.HexArray;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TLVDeserializerTest {

    @Test
    public void testIsObjectInstanceOrResource() throws Exception {
        byte[] tlvR = new byte[]{
            (byte) 0b11_0_00_011,
            (byte) 0,};
        byte[] tlvO = new byte[]{
            (byte) 0b00_0_01_000,
            (byte) 0,};

        assertFalse(TLVDeserializer.isObjectInstance(tlvR));
        assertFalse(TLVDeserializer.isMultipleResource(tlvR));
        assertFalse(TLVDeserializer.isResourceInstance(tlvR));
        assertTrue(TLVDeserializer.isResource(tlvR));
        assertFalse(TLVDeserializer.isResource(tlvO));
        assertFalse(TLVDeserializer.isMultipleResource(tlvO));
        assertFalse(TLVDeserializer.isResourceInstance(tlvO));
        assertTrue(TLVDeserializer.isObjectInstance(tlvO));
    }

    @Test
    public void deserializeResourceTLV() {
        byte[] tlv = createResourceTLV();

        assertTrue(TLVDeserializer.isResource(tlv));
        List<LWM2MResource> resources = TLVDeserializer.deserializeResources(tlv);
        assertThat(resources, hasSize(1));
        assertThat(resources.get(0).getId().intValue(), equalTo(0));
        assertArrayEquals(Utf8Bytes.of("ARM"), resources.get(0).getValue());
        assertFalse(resources.get(0).hasNestedInstances());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeResourceTLVasObjectInstance() throws Exception {
        TLVDeserializer.deserialiseObjectInstances(createResourceTLV());
    }

    private static byte[] createResourceTLV() {
        return new byte[]{
            (byte) 0b11_0_00_011,
            (byte) 0,
            'A', 'R', 'M',};
    }

    @Test
    public void deserializeResourceWithZeroLength() throws Exception {
        byte[] tlv = new byte[]{
                (byte) 0b11_0_00_000,
                (byte) 0 };

        List<LWM2MResource> resources = TLVDeserializer.deserializeResources(tlv);
        assertThat(resources, hasSize(1));
        assertThat(resources.get(0).getId().intValue(), equalTo(0));
        assertThat(resources.get(0).getStringValue(), equalTo(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeResourceWhereLengthFieldIsLess() throws Exception {
        byte[] tlv = new byte[]{
            (byte) 0b11_0_00_010,
            (byte) 0,
            'A', 'R', 'M'};

        TLVDeserializer.deserializeResources(tlv);
    }

    @Test
    public void deserializeResourceWhereLengthFieldIsGreater() throws Exception {
        byte[] tlv = new byte[]{
            (byte) 0b11_0_00_011,
            (byte) 0,
            'A', 'R'};

        List<LWM2MResource> resources = TLVDeserializer.deserializeResources(tlv);
        assertThat(resources, hasSize(1));
        assertThat(resources.get(0).getId().intValue(), equalTo(0));
        assertArrayEquals(Utf8Bytes.of("AR\0"), resources.get(0).getValue());
        assertFalse(resources.get(0).hasNestedInstances());
    }

    @Test
    public void deserializeMultipleResourceTLV() {
        byte[] tlv = new byte[]{
            (byte) 0b10_0_00_110,
            (byte) 6,
            (byte) 0b01_0_00_001,
            (byte) 0,
            (byte) 0x01,
            (byte) 0b01_0_00_001,
            (byte) 1,
            (byte) 0x05,};

        assertTrue(TLVDeserializer.isMultipleResource(tlv));
        List<LWM2MResource> resources = TLVDeserializer.deserializeResources(tlv);
        assertThat(resources, hasSize(1));
        assertThat(resources.get(0).getId().intValue(), equalTo(6));
        assertThat(resources.get(0).getNestedInstances(), hasSize(2));
        assertThat(resources.get(0).getNestedInstances().get(0).getId().intValue(), equalTo(0));
        assertEquals(1, resources.get(0).getNestedInstances().get(0).getValue()[0]);
        assertThat(resources.get(0).getNestedInstances().get(1).getId().intValue(), equalTo(1));
        assertEquals(5, resources.get(0).getNestedInstances().get(1).getValue()[0]);
    }

    @Test
    public void deserializeMultipleObjectInstances() throws Exception {
        byte[] acoTLV = createObjectInstanceTLV();

        List<LWM2MObjectInstance> objects = TLVDeserializer.deserialiseObjectInstances(acoTLV);
        assertThat(objects, hasSize(2));
        assertThat(objects.get(0).getId().intValue(), equalTo(0));
        assertThat(objects.get(0).getResources(), hasSize(3));
        assertThat(objects.get(1).getId().intValue(), equalTo(1));
        assertThat(objects.get(1).getResources(), hasSize(3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void serializeObjectInstanceAsResource() throws Exception {
        TLVDeserializer.deserializeResources(createObjectInstanceTLV());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeRandomObjectInstance() throws Exception {
        TLVDeserializer.deserialiseObjectInstances(Utf8Bytes.of("342fd78b"));
    }

    @Test
    public void deserializeCertificates() throws Exception {
        String tlvString = "EAACycECAtADATQwggEwMIHVoAMCAQICAQEwDAYIKoZIzj0EAwIFADAQMQ4wDAYDVQQDDAVOU1AtMTAeFw0xNDA1MTMwNzQ2NDdaFw0xNDA1MjYwNzQ2NDdaMDAxDTALBgNVBAcTBE91bHUxETAPBgNVBAMTCG5vZGUtMDAxMQwwCgYDVQQKEwNBUk0wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT+MqviUOwxcdmY1EEjtJ6abf4Q1zSrp8vlYgAJD2j6a+ksLie/GHyon/8403pXaor+IfLRU0KuLb20+47CyoFLMAwGCCqGSM49BAMCBQADSAAwRQIgCHCKSRjEhChhCvO4njwanjpZh7qBlGAVwjRvE+m8SRACIQDvEvlUVSGNnHRvw6PTi+tae6HXX40L9HZEUJ4reBHuNdAEAS4wggEqMIHRoAMCAQICAQEwCgYIKoZIzj0EAwIwLjERMA8GA1UEAwwIQVJNLVRFU1QxDDAKBgNVBAoMA0FSTTELMAkGA1UEBhMCRkkwHhcNMTQwNDAzMDgxODE4WhcNMTYwNDAyMDgxODE4WjAQMQ4wDAYDVQQDDAVOU1AtMTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABLOijdGrCNkRYl2p5A7wxO0ZiUb+NmvGh0B7gULpi77fZqE1qR8kMxIScsBCtG+d1PwUYMvp5qmmlHWdStHQuBMwCgYIKoZIzj0EAwIDSAAwRQIhAK2GhhmBF3hiJPlXNqER3vIgWEPbkM4OM6UjgSfphgeiAiALm+ou5x2voDQcZmWvfxV10bDlWDae0Px29sTJcEiuWMgFQzBBAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBCcwJQIBAQQg6Pu7QIblW3dF+C995TkThuTr0FA/MetQmqxZRVRCP3TIABNjb2FwczovL1s6OjFdOjYxNjE2";
        byte[] tlv = BaseEncoding.base64().decode(tlvString);
        List<LWM2MObjectInstance> decoded = TLVDeserializer.deserialiseObjectInstances(tlv);
        assertThat(decoded, hasSize(1));
        List<LWM2MResource> resources = decoded.get(0).getResources();
        assertThat(resources, hasSize(5));
        assertThat(resources.get(0).getId().intValue(), equalTo(2));
        assertThat(resources.get(1).getId().intValue(), equalTo(3));
        assertThat(resources.get(2).getId().intValue(), equalTo(4));
        assertThat(resources.get(3).getId().intValue(), equalTo(5));
        assertThat(resources.get(4).getId().intValue(), equalTo(0));
        assertThat(resources.get(1).getValue().length, equalTo(308));
        assertThat(resources.get(2).getValue().length, equalTo(302));
        assertThat(resources.get(3).getValue().length, equalTo(67));
    }

    @Test
    public void deserializeFromHexString() throws Exception {
        String hex = "08012bc80010636f61703a2f2f6c6f63616c686f7374c40133363030c10231c2033630c304363030c10530c10730";
        byte[] tlv = HexArray.fromHex(hex);
        List<LWM2MObjectInstance> decoded = TLVDeserializer.deserialiseObjectInstances(tlv);
        assertThat(decoded, hasSize(1));
        LWM2MObjectInstance instance = decoded.get(0);
        assertThat(instance.getResource(LWM2MID.$0).getStringValue(), equalTo("coap://localhost"));
    }

    private static byte[] createObjectInstanceTLV() {
        LWM2MObjectInstance aco1 = new LWM2MObjectInstance(Arrays.asList(
                new LWM2MResource(LWM2MID.from(0), 0x3),
                new LWM2MResource(LWM2MID.from(2), Arrays.asList(
                                new LWM2MResourceInstance(LWM2MID.from(1), 0b11_10_0000),
                                new LWM2MResourceInstance(LWM2MID.from(2), 0b10_00_0000))
                ),
                new LWM2MResource(LWM2MID.from(3), 0x01)
        ));
        LWM2MObjectInstance aco2 = new LWM2MObjectInstance(Arrays.asList(
                new LWM2MResource(LWM2MID.from(0), 0x4),
                new LWM2MResource(LWM2MID.from(2), Arrays.asList(
                                new LWM2MResourceInstance(LWM2MID.from(1), 0b10_00_0000),
                                new LWM2MResourceInstance(LWM2MID.from(2), 0b11_10_0000))
                ),
                new LWM2MResource(LWM2MID.from(3), 0x02)
        ));

        return TLVSerializer.serializeObjectInstances(Arrays.asList(aco1, aco2));
    }
}
