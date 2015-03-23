/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

import java.io.Serializable;
import java.util.Arrays;

/**
* @author szymon
*/
class RawOption implements Comparable<RawOption>, Serializable {

    int optNumber;
    byte[][] optValues;

    static RawOption fromString(int num, String[] stringVal) {
        return new RawOption(num, DataConvertingUtility.stringArrayToBytes(stringVal));
    }

    static RawOption fromUint(int num, Long uintVal) {
        return new RawOption(num, new byte[][]{DataConvertingUtility.convertVariableUInt(uintVal)});
    }

    static RawOption fromString(int num, String stringVal) {
        return new RawOption(num, new byte[][]{DataConvertingUtility.encodeString(stringVal)});
    }

    static RawOption fromUint(int num, short[] uintVal) {
        return new RawOption(num, DataConvertingUtility.writeVariableUInt(uintVal));
    }

    static RawOption fromEmpty(int num) {
        return new RawOption(num, new byte[][]{{}});
    }

    RawOption(int optNumber, byte[][] optValues) {
        this.optNumber = optNumber;
        this.optValues = optValues;
    }

    RawOption(int optNumber, byte[] singleOptValue) {
        this.optNumber = optNumber;
        this.optValues = new byte[1][];
        this.optValues[0] = singleOptValue;
    }

    int getNumber() {
        return optNumber;
    }

    byte[][] getValues() {
        return optValues;
    }

    byte[] getFirstValue() {
        return (optValues.length > 0) ? optValues[0] : null;
    }

    @Override
    public int compareTo(RawOption o) {
        return (optNumber < o.optNumber ? -1 : (optNumber == o.optNumber ? 0 : 1));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + this.optNumber;
        hash = 47 * hash + Arrays.deepHashCode(this.optValues);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RawOption other = (RawOption) obj;
        if (this.optNumber != other.optNumber) {
            return false;
        }
        if (!Arrays.deepEquals(this.optValues, other.optValues)) {
            return false;
        }
        return true;
    }
}
