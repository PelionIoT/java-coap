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
package com.mbed.coap.linkformat;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author szymon
 */
public class PToken implements CharSequence, Serializable {

    private static int validRangeStart = 33; //'!'
    private static int validRangeStop = 126; //'~'
    private static char[] nonValidInRange = new char[]{',', ';', '\\'};
    private final String val;

    public PToken(String val) throws IllegalArgumentException {
        if (!validate(val)) {
            throw new IllegalArgumentException("Illegal character in a ptoken");
        }
        this.val = val;
    }

    private static boolean validate(String ptoken) {
        for (int i = 0; i < ptoken.length(); i++) {
            char c = ptoken.charAt(i);
            if (!(c >= validRangeStart && c <= validRangeStop && Arrays.binarySearch(nonValidInRange, c) < 0)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int length() {
        return val.length();
    }

    @Override
    public char charAt(int index) {
        return val.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return val.subSequence(start, end);
    }

    @Override
    public String toString() {
        return val;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + (this.val != null ? this.val.hashCode() : 0);
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
        final PToken other = (PToken) obj;
        if ((this.val == null) ? (other.val != null) : !this.val.equals(other.val)) {
            return false;
        }
        return true;
    }

}
