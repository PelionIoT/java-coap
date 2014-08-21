/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tlv;

/**
 * Defines timestamp for TLV objects.
 *
 * @author szymon
 */
public class TimestampedTLVObject implements Comparable<TimestampedTLVObject> {

    public static final short TYPE_CURRENT_TIMESTAMP = 0;
    public static final short TYPE_OFFSET_TIMESTAMP = 1;
    private Long measurementTimestamp;
    private final boolean isLatest;
    private final TLVObject tlvObject;

    /**
     * Creates instance.
     *
     * @param measurementTimestamp measurement UNIX timestamp in milliseconds
     * @param isLatest true if is latest measurement
     * @param tlvObject tlv object
     */
    public TimestampedTLVObject(Long measurementTimestamp, boolean isLatest, TLVObject tlvObject) {
        this.measurementTimestamp = measurementTimestamp;
        this.isLatest = isLatest;
        this.tlvObject = tlvObject;
    }

    /**
     * Returns UNIX timestamp in milliseconds for this TLV object. If null, then
     * timestamp is unavailable for this measurement.
     *
     * @return UNIX timestamp in milliseconds
     */
    public Long getTimestamp() {
        return measurementTimestamp;
    }

    void setTimestamp(Long timestamp) {
        this.measurementTimestamp = timestamp;
    }

    /**
     * Tells if given measurement is latest.
     *
     * @return true for latest measurement
     */
    public boolean isLatest() {
        return isLatest;
    }

    /**
     * Returns TLV object.
     *
     * @return tlv object
     */
    public TLVObject getTlvObject() {
        return tlvObject;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + (this.measurementTimestamp != null ? this.measurementTimestamp.hashCode() : 0);
        hash = 83 * hash + (this.isLatest ? 1 : 0);
        hash = 83 * hash + (this.tlvObject != null ? this.tlvObject.hashCode() : 0);
        return hash;
    }

    @Override
    @SuppressWarnings("PMD.CyclomaticComplexity")  //ignore for equals method
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TimestampedTLVObject other = (TimestampedTLVObject) obj;
        if (this.measurementTimestamp != other.measurementTimestamp && (this.measurementTimestamp == null || !this.measurementTimestamp.equals(other.measurementTimestamp))) {
            return false;
        }
        if (this.isLatest != other.isLatest) {
            return false;
        }
        if (this.tlvObject != other.tlvObject && (this.tlvObject == null || !this.tlvObject.equals(other.tlvObject))) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(TimestampedTLVObject o) {
        return o.measurementTimestamp.compareTo(measurementTimestamp);
    }
}
