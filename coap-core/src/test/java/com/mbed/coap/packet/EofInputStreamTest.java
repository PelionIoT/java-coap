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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Test;


public class EofInputStreamTest {

    @Test
    void shouldNotWrapWhenAlreadyStrictInputStream() {
        EofInputStream in = EofInputStream.wrap(new ByteArrayInputStream(new byte[]{1, 2}));

        assertSame(in, EofInputStream.wrap(in));
    }

    @Test
    public void should_thrown_eof_when_end_of_stream() throws IOException {
        EofInputStream in = EofInputStream.wrap(new ByteArrayInputStream(new byte[]{1, 2}));

        assertEquals(1, in.read());
        assertEquals(1, in.available());
        assertEquals(2, in.read());
        assertEquals(0, in.available());

        assertThatThrownBy(in::read).isExactlyInstanceOf(EOFException.class);
    }

    @Test
    public void should_thrown_eof_when_reading_bytes() throws IOException {
        EofInputStream in = EofInputStream.wrap(new ByteArrayInputStream("testtest".getBytes()));

        assertEquals(5, in.read(new byte[6], 0, 5));
        assertEquals(3, in.read(new byte[6], 0, 5));

        assertThrows(EOFException.class, () -> in.read(new byte[6], 0, 5));
    }

}