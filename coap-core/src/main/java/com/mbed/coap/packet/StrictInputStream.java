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
        int totalRead = 0;

        //loop until all data is read or EOF
        while (totalRead < len) {
            int r = inputStream.read(bytes, totalRead, len - totalRead);
            if (r == -1) {
                throw new EOFException();
            }
            totalRead += r;
        }
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
    public int available() throws IOException {
        return inputStream.available();
    }
}
