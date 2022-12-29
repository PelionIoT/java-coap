/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream decorator that throws EOFException if data in not available.
 */
class EofInputStream extends InputStream {
    private final InputStream inputStream;

    private EofInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int ret = inputStream.read(b, off, len);
        if (ret == -1) {
            throw new EOFException();
        }
        return ret;
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

    static EofInputStream wrap(InputStream inputStream) {
        if (inputStream instanceof EofInputStream) {
            return (EofInputStream) inputStream;
        } else {
            return new EofInputStream(inputStream);
        }
    }
}
