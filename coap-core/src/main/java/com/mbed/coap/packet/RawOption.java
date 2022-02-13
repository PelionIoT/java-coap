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

import java.util.Arrays;

/**
 * @author szymon
 */
final class RawOption implements Comparable<RawOption> {

    final int optNumber;
    final Opaque[] optValues;

    static RawOption fromString(int num, String[] strArray) {
        Opaque[] bt;
        int skip = 0;
        if (strArray.length > 0 && strArray[0].length() == 0) {
            bt = new Opaque[strArray.length - 1];
            skip = 1;
        } else {
            bt = new Opaque[strArray.length];
        }
        for (int i = 0; i < bt.length; i++) {
            bt[i] = Opaque.of(strArray[i + skip]);
        }
        return new RawOption(num, bt);
    }

    static RawOption fromUint(int num, long uintVal) {
        return new RawOption(num, new Opaque[]{Opaque.variableUInt(uintVal)});
    }

    static RawOption fromString(int num, String stringVal) {
        return new RawOption(num, new Opaque[]{Opaque.of(stringVal)});
    }

    static RawOption fromEmpty(int num) {
        return new RawOption(num, new Opaque[]{Opaque.EMPTY});
    }

    RawOption(int optNumber, Opaque[] optValues) {
        this.optNumber = optNumber;
        this.optValues = optValues;
    }

    RawOption(int optNumber, Opaque singleOptValue) {
        this.optNumber = optNumber;
        this.optValues = new Opaque[1];
        this.optValues[0] = singleOptValue;
    }

    Opaque getFirstValue() {
        return (optValues.length > 0) ? optValues[0] : null;
    }

    @Override
    public int compareTo(RawOption o) {
        return (Integer.compare(optNumber, o.optNumber));
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
        if (!Arrays.equals(this.optValues, other.optValues)) {
            return false;
        }
        return true;
    }
}
