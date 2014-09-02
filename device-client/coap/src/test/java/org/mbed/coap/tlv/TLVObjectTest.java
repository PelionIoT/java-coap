package org.mbed.coap.tlv;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class TLVObjectTest {

    @Test
    public void testSerialization() throws IOException {
        TLVObject tlvObject = new TLVObject((short) 1, "1234");
        assertArrayEquals(new byte[]{0, 1, 0, 4, '1', '2', '3', '4'}, tlvObject.serialize());

        tlvObject = new TLVObject((short) 258, "12");
        assertArrayEquals(new byte[]{1, 2, 0, 2, '1', '2'}, tlvObject.serialize());

        tlvObject = new TLVObject((short) 1, "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        assertEquals(1, tlvObject.serialize()[2]);
        assertEquals(44, tlvObject.serialize()[3]);
        assertEquals(304, tlvObject.serialize().length);

        tlvObject = new TLVObject((short) 0, "");
        assertArrayEquals(new byte[]{0, 0, 0, 0}, tlvObject.serialize());

        tlvObject = new TLVObject((short) 0xFFFF, "a");
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, 0, 1, 'a'}, tlvObject.serialize());

    }

    @Test
    public void testListSerialization() throws IOException {

        TLVObject tlvOb1 = new TLVObject((short) 1, "a");
        TLVObject tlvOb2 = new TLVObject((short) 2, "bbb");
        TLVObject tlvOb3 = new TLVObject((short) 3, "cc");

        byte[] batchTlv = TLVUtils.serializeList(Arrays.asList(tlvOb1, tlvOb2, tlvOb3));

        assertEquals(18, batchTlv.length);
        assertEquals('a', batchTlv[4]);
        assertEquals('b', batchTlv[9]);
        assertEquals('c', batchTlv[16]);
    }

    @Test
    public void testDeserialize() throws IOException {

        TLVObject tlvOb1 = new TLVObject((short) 1, "abc");
        TLVObject tlvOb1cp = TLVObject.deserialize(tlvOb1.serialize());

        assertEquals(tlvOb1, tlvOb1cp);
        assertEquals(tlvOb1.getType(), tlvOb1cp.getType());
        assertArrayEquals(tlvOb1.getValue(), tlvOb1cp.getValue());
        assertEquals(tlvOb1.hashCode(), tlvOb1cp.hashCode());
    }

    @Test
    public void deserializeNonPrintableCharacters() throws IOException {

        byte[] bt = new byte[]{0x00, 0x10, 0x00, 0x03, 'f', 31, 'f'};
        TLVObject tlv = TLVObject.deserialize(bt);
        assertArrayEquals(new byte[]{'f', 31, 'f'}, tlv.getValue());
    }

    @Test(expected = EOFException.class)
    public void deserializeMalformatted() throws IOException {

        byte[] bt = new byte[]{0x00, 0x10, 0x00, (byte) 0xFF, 'f', 'd', 'f'};
        TLVObject.deserialize(bt);
    }

    @Test
    public void deserializeSpecial() throws IOException {

        byte[] bt = new byte[]{(byte) 0xFF, (byte) 0xFF, 0x00, 0x00};
        TLVObject tlv = TLVObject.deserialize(bt);
        assertArrayEquals(new byte[]{}, tlv.getValue());
        assertEquals((short) 0xFFFF, tlv.getType());
    }

    @Test
    public void testDeserializeList() throws IOException {

        TLVObject tlvOb1 = new TLVObject((short) 1, "abc");
        TLVObject tlvOb2 = new TLVObject((short) 100, "fdsfs");
        List<TLVObject> tlvList = new LinkedList<>();
        tlvList.add(tlvOb1);
        tlvList.add(tlvOb2);

        List<TLVObject> tlvList_cp = TLVUtils.deserializeList(TLVUtils.serializeList(tlvList));

        assertEquals(tlvList, tlvList_cp);
        //assertEquals(tlvOb1.hashCode(), tlvOb1cp.hashCode());
    }

    @Test
    public void opaque() throws IOException {
        byte[] data = new byte[255];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        TLVObject tlv1 = new TLVObject((short) 33, data);
        TLVObject tlv2 = TLVObject.deserialize(tlv1.serialize());
        assertEquals(tlv1, tlv2);
        assertArrayEquals(data, tlv1.getValue());
        assertArrayEquals(data, tlv2.getValue());
    }
}
