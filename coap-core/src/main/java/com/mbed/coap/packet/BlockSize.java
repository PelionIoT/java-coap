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

/**
 * @author szymon
 */
public enum BlockSize {

    S_16(4, false), S_32(5, false), S_64(6, false), S_128(7, false), S_256(8, false), S_512(9, false), S_1024(10, false), S_1024_BERT(10, true);
    byte szx;
    boolean bert;

    BlockSize(int szx, boolean bert) {
        this.szx = (byte) (szx - 4);
        this.bert = bert;
    }

    public int getSize() {
        return 1 << (szx + 4);
    }

    public int numberOfBlocksPerMessage(int totalSize) {
        return bert ? totalSize / getSize() : 1;
    }


    public static BlockSize fromRawSzx(byte rawSzx) {
        return values()[rawSzx];
    }

    public byte toRawSzx() {
        if (bert) {
            return 7;
        } else {
            return szx;
        }
    }

    public boolean isBert() {
        return bert;
    }

}
