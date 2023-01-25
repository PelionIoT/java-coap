/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.cli.providers;

import static com.mbed.coap.packet.Opaque.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.Opaque;
import org.junit.jupiter.api.Test;

class PairTest {
    @Test
    void createFromString() {
        assertEquals(Pair.of("aaa", "1010"), Pair.split("aaa:1010", ':'));
        assertEquals(Pair.of("aaa", ""), Pair.split("aaa:", ':'));
        assertEquals(Pair.of("", "1010"), Pair.split(":1010", ':'));
        assertEquals(Pair.of("", ""), Pair.split(":", ':'));
    }

    @Test
    void mapValue() {
        assertEquals(Pair.of("aaa", ofBytes(1, 2)), Pair.of("aaa", "0102").mapValue(Opaque::decodeHex));
    }
}