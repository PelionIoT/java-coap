/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mbed.coap.tlv;

import org.mbed.coap.tlv.TLVObject;
import org.mbed.coap.tlv.TLVUtils;
import org.mbed.coap.tlv.TimestampedTLVObject;
import java.io.IOException;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class TimestampedTLVObjectTest {

    @Test
    public void testDeserializeHistorical() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"));
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "60"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        tlvList.add(new TLVObject((short) 1002, "101 W"));
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "120"));
        tlvList.add(new TLVObject((short) 1001, "23 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        List<TimestampedTLVObject> ttlvList = TLVUtils.deserializeTimestampedList(tlvMsg);

        assertEquals(3, ttlvList.size());
        assertEquals(new TimestampedTLVObject(1360333060000L, false, new TLVObject((short) 1001, "20 C")), ttlvList.get(0));
        assertEquals(new TimestampedTLVObject(1360333060000L, false, new TLVObject((short) 1002, "101 W")), ttlvList.get(1));
        assertEquals(new TimestampedTLVObject(1360333000000L, false, new TLVObject((short) 1001, "23 C")), ttlvList.get(2));

    }

    @Test
    public void testDeserializeLatest() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        tlvList.add(new TLVObject((short) 1002, "101 W"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        List<TimestampedTLVObject> ttlvList = TLVUtils.deserializeTimestampedList(tlvMsg);

        assertEquals(2, ttlvList.size());
        assertEquals(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1001, "20 C")), ttlvList.get(0));
        assertEquals(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1002, "101 W")), ttlvList.get(1));
    }

    @Test
    public void testDeserializeLatestAndHistorical() throws ParseException, IOException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        tlvList.add(new TLVObject((short) 1002, "101 W"));
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "120"));
        tlvList.add(new TLVObject((short) 1001, "23 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        List<TimestampedTLVObject> ttlvList = TLVUtils.deserializeTimestampedList(tlvMsg);

        assertEquals(3, ttlvList.size());
        assertEquals(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1001, "20 C")), ttlvList.get(0));
        assertEquals(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1002, "101 W")), ttlvList.get(1));
        assertEquals(new TimestampedTLVObject(1360333000000L, false, new TLVObject((short) 1001, "23 C")), ttlvList.get(2));
    }

    @Test
    public void testDeserializeLatestNoTS() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        tlvList.add(new TLVObject((short) 1002, "101 W"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        List<TimestampedTLVObject> ttlvList = TLVUtils.deserializeTimestampedList(tlvMsg);

        assertEquals(2, ttlvList.size());
        assertEquals(new TimestampedTLVObject(null, true, new TLVObject((short) 1001, "20 C")), ttlvList.get(0));
        assertEquals(new TimestampedTLVObject(null, true, new TLVObject((short) 1002, "101 W")), ttlvList.get(1));
        assertEquals((new TimestampedTLVObject(null, true, new TLVObject((short) 1002, "101 W"))).hashCode(), ttlvList.get(1).hashCode());
    }

    @Test
    public void testDeserializeTSNoContent() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        List<TimestampedTLVObject> ttlvList = TLVUtils.deserializeTimestampedList(tlvMsg);

        assertEquals(0, ttlvList.size());
    }

    @Test(expected = ParseException.class)
    public void testMalformattedTimestamp() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360dupa333120"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        TLVUtils.deserializeTimestampedList(tlvMsg);
    }

    @Test(expected = ParseException.class)
    public void testMalformattedTimestamp2() throws IOException, ParseException {
        //--- build tlv message ---
        Long malformattedTsSec = 0x1FFFFFFFFL; //5 bytes long
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, malformattedTsSec.toString()));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        TLVUtils.deserializeTimestampedList(tlvMsg);
    }

    @Test(expected = ParseException.class)
    public void testMalformattedOffset() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "1360dupa"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        TLVUtils.deserializeTimestampedList(tlvMsg);
    }

    @Test(expected = ParseException.class)
    public void testMalformattedOffset2() throws IOException, ParseException {
        //--- build tlv message ---
        List<TLVObject> tlvList = new LinkedList<TLVObject>();
        tlvList.add(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "-1360"));
        tlvList.add(new TLVObject((short) 1001, "20 C"));
        byte[] tlvMsg = TLVUtils.serializeList(tlvList);
        //------------------------

        TLVUtils.deserializeTimestampedList(tlvMsg);
    }

    @Test(expected = ParseException.class)
    public void testMalformattedMessage() throws ParseException {
        byte[] tlvMsg = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

        TLVUtils.deserializeTimestampedList(tlvMsg);
    }

    @Test
    public void testTimestampedTLVObject() {
        TimestampedTLVObject ttlv = new TimestampedTLVObject(1360333120000L, false, new TLVObject((short) 1006, "dd"));
        assertFalse(ttlv.isLatest());

        ttlv.setTimestamp(null);
        assertFalse(ttlv.isLatest());

        ttlv = new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1006, "dd"));
        assertTrue(ttlv.isLatest());
    }

    @Test
    public void testSerializeHistoricalSorted() throws IOException {
        //--- build tlv message ---
        List<TimestampedTLVObject> ttlvList = new LinkedList<TimestampedTLVObject>();
        ttlvList.add(new TimestampedTLVObject(1360333060000L, false, new TLVObject((short) 1001, "20 C")));
        ttlvList.add(new TimestampedTLVObject(1360333000000L, false, new TLVObject((short) 1002, "106 W")));
        ttlvList.add(new TimestampedTLVObject(1360333060000L, false, new TLVObject((short) 1002, "103 W")));
        //------------------------

        byte[] data = TLVUtils.serializeTimestampedList(ttlvList, 1360333120000L);
        List<TLVObject> tlvList = TLVUtils.deserializeList(data);

        assertEquals(6, tlvList.size());
        assertEquals(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"), tlvList.get(0));
        assertEquals(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "60"), tlvList.get(1));
        assertEquals(new TLVObject((short) 1001, "20 C"), tlvList.get(2));
        assertEquals(new TLVObject((short) 1002, "103 W"), tlvList.get(3));
        assertEquals(new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, "120"), tlvList.get(4));
        assertEquals(new TLVObject((short) 1002, "106 W"), tlvList.get(5));

    }

    @Test
    public void testSerializeLatest() throws IOException {
        //--- build tlv message ---
        List<TimestampedTLVObject> ttlvList = new LinkedList<TimestampedTLVObject>();
        ttlvList.add(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1001, "20 C")));
        ttlvList.add(new TimestampedTLVObject(1360333120000L, true, new TLVObject((short) 1002, "106 W")));
        //------------------------

        byte[] data = TLVUtils.serializeTimestampedList(ttlvList, 1360333120000L);
        List<TLVObject> tlvList = TLVUtils.deserializeList(data);

        assertEquals(3, tlvList.size());
        assertEquals(new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, "1360333120"), tlvList.get(0));
        assertEquals(new TLVObject((short) 1001, "20 C"), tlvList.get(1));
        assertEquals(new TLVObject((short) 1002, "106 W"), tlvList.get(2));

    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeHistoricalWithWrongCurrentTimestamp() throws IOException {
        //--- build tlv message ---
        List<TimestampedTLVObject> ttlvList = new LinkedList<TimestampedTLVObject>();
        ttlvList.add(new TimestampedTLVObject(1360333060000L, true, new TLVObject((short) 1001, "20 C")));
        ttlvList.add(new TimestampedTLVObject(1360333000000L, true, new TLVObject((short) 1002, "106 W")));
        ttlvList.add(new TimestampedTLVObject(1360333060000L, true, new TLVObject((short) 1002, "103 W")));
        //------------------------

        TLVUtils.serializeTimestampedList(ttlvList, 1000333120000L);
    }

    @Test
    public void testSerializeHistoricalWithEmptyList() throws IOException {
        List<TimestampedTLVObject> ttlvList = new LinkedList<TimestampedTLVObject>();
        assertEquals(0, TLVUtils.serializeTimestampedList(ttlvList, 1000333120000L).length);
    }

    @Test(expected = NullPointerException.class)
    public void testSerializeHistoricalWithNullTimestamp() throws IOException {
        //--- build tlv message ---
        List<TimestampedTLVObject> ttlvList = new LinkedList<TimestampedTLVObject>();
        ttlvList.add(new TimestampedTLVObject(1360333060000L, true, new TLVObject((short) 1001, "20 C")));
        ttlvList.add(new TimestampedTLVObject(null, true, new TLVObject((short) 1002, "106 W")));
        ttlvList.add(new TimestampedTLVObject(1360333060000L, true, new TLVObject((short) 1002, "103 W")));
        //------------------------

        TLVUtils.serializeTimestampedList(ttlvList, 1360333120000L);

    }
}
