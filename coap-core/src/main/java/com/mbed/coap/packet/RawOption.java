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

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author szymon
 */
final class RawOption implements Comparable<RawOption>, Serializable {

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
