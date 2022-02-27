/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minor CoapPacket binary read/write utility methods with specific optional checks.
 */
class PacketUtils {

    static byte[] readN(StrictInputStream is, int bytesCount, boolean orBlock) throws IOException {
        assertEnoughData(is, bytesCount, orBlock);
        return is.readBytes(bytesCount);
    }


    static long read32(InputStream is, boolean orBlock) throws IOException {
        assertEnoughData(is, 4, orBlock);
        long ret = is.read() << 24;
        ret |= is.read() << 16;
        ret |= is.read() << 8;
        ret |= is.read();

        return ret;
    }

    static int read16(InputStream is, boolean orBlock) throws IOException {
        assertEnoughData(is, 2, orBlock);
        int ret = is.read() << 8;
        ret |= is.read();
        return ret;
    }

    static int read8(InputStream is, boolean orBlock) throws IOException {
        assertEnoughData(is, 1, orBlock);
        return is.read();
    }

    private static void assertEnoughData(InputStream is, int expectedMinimum, boolean orBlock) throws IOException {
        if (orBlock) {
            return;
        }
        if (is.available() < expectedMinimum) {
            throw new NotEnoughDataException();
        }
    }

    static class NotEnoughDataException extends IOException {
    }

    static void write8(OutputStream os, int data) throws IOException {
        os.write(data);
    }

    static void write16(OutputStream os, int data) throws IOException {
        os.write((data >> 8) & 0xFF);
        os.write((data >> 0) & 0xFF);
    }

    static void write32(OutputStream os, long data) throws IOException {
        os.write((int) ((data >> 24) & 0xFF));
        os.write((int) ((data >> 16) & 0xFF));
        os.write((int) ((data >> 8) & 0xFF));
        os.write((int) ((data >> 0) & 0xFF));
    }
}
