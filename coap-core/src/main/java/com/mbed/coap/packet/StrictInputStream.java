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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by olesmi01 on 24.07.2017.
 * Extracted inner class, implements InputStream which throws EOFException if data can't be read.
 */
class StrictInputStream extends InputStream {
    private final InputStream inputStream;

    StrictInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public byte[] readBytes(int len) throws IOException {
        byte[] bytes = new byte[len];
        read(bytes);
        return bytes;
    }

    @Override
    public int read() throws IOException {
        int val = inputStream.read();
        if (val < 0) {
            throw new EOFException();
        }
        return val;
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (b.length == 0) {
            return 0;
        }
        if (inputStream.read(b) != b.length) {
            throw new EOFException();
        }
        return b.length;
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }
}
