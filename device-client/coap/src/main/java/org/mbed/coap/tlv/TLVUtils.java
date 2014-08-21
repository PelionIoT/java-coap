/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tlv;

import org.mbed.coap.utils.ByteArrayBackedInputStream;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for serializing and parsing TLV messages.
 *
 * @author szymon
 */
public class TLVUtils {

    /**
     * Serializes timestamped tlv objects into byte array.
     *
     * @param tlvList list of timestamped tlv objects
     * @param currentTimestamp current (base) unix timestamp in milliseconds
     * @return serialized byte array
     * @throws IOException
     */
    public static byte[] serializeTimestampedList(List<TimestampedTLVObject> tlvList, long currentTimestamp) throws IOException {
        if (tlvList.isEmpty()) {
            return new byte[0];
        }
        Collections.sort(tlvList);
        if (currentTimestamp < tlvList.get(0).getTimestamp()) {
            throw new IllegalArgumentException("Current-timestamp is smaller than one in tlv-object.");
        }
        ByteArrayBackedOutputStream baos = new ByteArrayBackedOutputStream();
        long lastOffset = 0;

        TLVObject timestampTlv = new TLVObject(TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP, Long.toString(currentTimestamp / 1000));
        timestampTlv.serialize(baos);

        for (TimestampedTLVObject tObj : tlvList) {
            long offset = currentTimestamp - tObj.getTimestamp();
            if (lastOffset != offset) {
                TLVObject offsetTlv = new TLVObject(TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP, Long.toString(offset / 1000));
                offsetTlv.serialize(baos);
            }
            lastOffset = offset;
            tObj.getTlvObject().serialize(baos);
        }
        return baos.toByteArray();
    }

    /**
     * De-serialize list of timestamped TLV objects from given byte array.
     *
     * @param data batch tlv message
     * @return list of timestamped TLV objects
     * @throws ParseException
     */
    @SuppressWarnings("PMD.AvoidRethrowingException") //catch anything but ParseException
    public static List<TimestampedTLVObject> deserializeTimestampedList(byte[] data) throws ParseException {
        try {
            ByteArrayBackedInputStream bais = new ByteArrayBackedInputStream(data);

            List<TimestampedTLVObject> tlvList = new LinkedList<TimestampedTLVObject>();

            Integer lastCurrTimestampSec = null;
            Integer lastOffsetSec = 0;
            while (bais.available() > 0) {
                TLVObject tlvObj = TLVObject.deserialize(bais);
                if (tlvObj.getType() == TimestampedTLVObject.TYPE_CURRENT_TIMESTAMP) {
                    lastCurrTimestampSec = Integer.parseInt(tlvObj.getValueAsString());
                    lastOffsetSec = 0;
                    continue;
                }
                if (tlvObj.getType() == TimestampedTLVObject.TYPE_OFFSET_TIMESTAMP) {
                    lastOffsetSec = Integer.parseInt(tlvObj.getValueAsString());
                    if (lastOffsetSec < 0) {
                        throw new ParseException("Offset can not be less that zero", 0);
                    }
                    continue;
                }

                Long timestampMillis = null;

                if (lastCurrTimestampSec != null) {
                    timestampMillis = (lastCurrTimestampSec - lastOffsetSec) * 1000L;
                }
                tlvList.add(new TimestampedTLVObject(timestampMillis, lastOffsetSec == 0, tlvObj));
            }

            return tlvList;
        } catch (ParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ParseException("Could not parse tlv batch: " + ex.getMessage(), 0);   //NOPMD
        }
    }

    /**
     * Serializes list of TLV object into byte array.
     *
     * @return serialized array of bytes
     * @throws IOException
     */
    public static byte[] serializeList(List<TLVObject> tlvList) throws IOException {
        ByteArrayBackedOutputStream baos = new ByteArrayBackedOutputStream();
        for (TLVObject tObj : tlvList) {
            tObj.serialize(baos);
        }
        return baos.toByteArray();
    }

    /**
     * De-serialize list of TLV object from given byte array.
     *
     * @param data batch tlv message
     * @return list of TLV objects
     * @throws IOException
     */
    public static List<TLVObject> deserializeList(byte[] data) throws IOException {
        ByteArrayBackedInputStream bais = new ByteArrayBackedInputStream(data);

        List<TLVObject> tlvList = new LinkedList<TLVObject>();

        while (bais.available() > 0) {
            TLVObject tlvObj = TLVObject.deserialize(bais);
            tlvList.add(tlvObj);
        }

        return tlvList;
    }
}
