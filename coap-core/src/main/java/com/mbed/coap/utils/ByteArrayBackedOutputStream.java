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
package com.mbed.coap.utils;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * Non blocking byte array output stream.
 *
 * @author szymon
 */
public class ByteArrayBackedOutputStream extends OutputStream {

    protected byte[] buffer;
    protected int position;

    public ByteArrayBackedOutputStream() {
        this(64);
    }

    public ByteArrayBackedOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        buffer = new byte[size];
    }

    @Override
    public void write(int b) {
        int newPosition = position + 1;
        if (newPosition > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newPosition));
        }
        buffer[position] = (byte) b;
        position = newPosition;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        int newPosition = position + len;
        if (newPosition > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newPosition));
        }
        System.arraycopy(b, off, buffer, position, len);
        position = newPosition;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, position);
    }

    public byte[] getByteArray() {
        return buffer;
    }

    public int size() {
        return position;
    }

    @Override
    public String toString() {
        return String.format("[pos: %d, buffer-len: %d]", position, buffer.length);
    }

    @Override
    public void close() {
        //does nothing
    }

}
