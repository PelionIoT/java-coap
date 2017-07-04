package com.mbed.coap.packet;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import com.mbed.coap.exception.CoapException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import org.junit.Test;
import org.mockito.Mockito;
import protocolTests.utils.CoapPacketBuilder;


public class CoapOverTcpTest extends CoapPacketTestBase {

    @Test
    public void deserializeAfterSerializeGivesBackACoapPacketWithSameData() throws CoapException {
        CoapPacket cp = CoapPacketBuilder.newCoapPacket().token(1234L).code(Code.C204_CHANGED).uriPath("/test").payload("some test payload").build();
        cp.setMessageType(null);

        byte[] rawCp = CoapOverTcp.serialize(cp);
        CoapPacket cp2 = CoapOverTcp.deserialize(null, new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapOverTcp.serialize(cp2));
        assertArrayEquals(DataConvertingUtility.convertVariableUInt(1234L), cp2.getToken());
        assertEquals(Code.C204_CHANGED, cp2.getCode());
        assertEquals("/test", cp2.headers().getUriPath());
        assertEquals("some test payload", cp2.getPayloadString());

        assertSimilar(cp, cp2);
    }

    @Test
    public void simpleNoPayload() throws CoapException {
        byte[] simpleBytes = new byte[]{0x01, 0x43, 0x7f};
        CoapPacket simplePacket = CoapOverTcp.deserialize(null, new ByteArrayInputStream(simpleBytes));

        assertEquals(Code.C203_VALID, simplePacket.getCode());
        assertArrayEquals(new byte[]{0x7f}, simplePacket.getToken());
        assertEquals(0, simplePacket.getPayload().length);
        assertEquals(null, simplePacket.getMethod());
        assertEquals(0, simplePacket.getMessageId());
        assertEquals(null, simplePacket.getMessageType());

        byte[] bytes2 = CoapOverTcp.serialize(simplePacket);
        assertArrayEquals(simpleBytes, bytes2);
        assertEquals(simplePacket, CoapOverTcp.deserialize(null, new ByteArrayInputStream(bytes2)));
    }

    @Test
    public void simpleSmallPayload() throws CoapException {
        byte[] token = new byte[]{0x7f};
        byte[] payload = new byte[]{6, 6, 6};

        assertSimplePacketSerializationAndDeserilization(token, payload);
    }

    @Test
    public void extendedLengthPayload() throws CoapException {
        byte[] token = new byte[]{0x7f};
        byte[] payload = new byte[57];
        new Random().nextBytes(payload);

        assertSimplePacketSerializationAndDeserilization(token, payload);
    }

    @Test
    public void extendedLengthBigPayload() throws CoapException {
        byte[] token = new byte[]{0x7f};
        byte[] payload = new byte[666];
        new Random().nextBytes(payload);

        assertSimplePacketSerializationAndDeserilization(token, payload);
    }

    @Test
    public void extendedLengthHugePayload() throws CoapException {
        byte[] token = new byte[]{0x7f};
        byte[] payload = new byte[65807];
        new Random().nextBytes(payload);

        assertSimplePacketSerializationAndDeserilization(token, payload);
    }

    @Test(expected = CoapException.class)
    public void bothMethodAndCodeUsed() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.DELETE, null, "", null);
        cp.setCode(Code.C202_DELETED);

        CoapOverTcp.serialize(cp);
    }

    @Test(expected = CoapException.class)
    public void inputStremException() throws CoapException, IOException {
        InputStream is = Mockito.mock(InputStream.class);
        when(is.read()).thenThrow(new IOException());

        CoapOverTcp.deserialize(null, is);
    }

    @Test(expected = CoapException.class)
    public void outputStremException() throws CoapException, IOException {
        OutputStream os = Mockito.mock(OutputStream.class);
        doThrow(new IOException()).when(os).write(any());

        CoapPacket cp = new CoapPacket(null, null, "", null);
        CoapOverTcp.writeTo(os, cp);
    }

    @Test
    public void coapPacketTest3_overTcp() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.PUT, null, "", null);
        cp.headers().setUriPath("/test2");
        cp.headers().setLocationPath("");
        cp.headers().setAccept(new short[]{});
        cp.setPayload("t�m� on varsin miel??$�");
        byte[] rawCp = CoapOverTcp.serialize(cp);
        CoapPacket cp2 = CoapOverTcp.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(cp2);
        assertArrayEquals(rawCp, CoapOverTcp.serialize(cp2));
        assertEquals(Method.PUT, cp2.getMethod());
        assertEquals(null, cp2.getMessageType());
        assertEquals("/test2", cp2.headers().getUriPath());
    }

    private void assertSimplePacketSerializationAndDeserilization(byte[] token, byte[] payload) throws CoapException {
        CoapPacket cp = new CoapPacket(null, null, "", null);
        cp.setToken(token);
        cp.setPayload(payload);

        byte[] rawCp = CoapOverTcp.serialize(cp);
        CoapPacket cp2 = CoapOverTcp.deserialize(null, new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapOverTcp.serialize(cp2));

        assertEquals(null, cp2.getCode());
        assertArrayEquals(token, cp2.getToken());
        assertEquals(payload.length, cp2.getPayload().length);
        assertArrayEquals(payload, cp2.getPayload());
        assertEquals(null, cp2.getMethod());
        assertEquals(0, cp2.getMessageId());
        assertEquals(null, cp2.getMessageType());
    }

}
