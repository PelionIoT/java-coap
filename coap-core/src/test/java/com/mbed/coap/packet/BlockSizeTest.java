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

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class BlockSizeTest {

    @Test
    public void number_of_blocks_per_message() {
        assertEquals(1, BlockSize.S_16.numberOfBlocksPerMessage(102));
        assertEquals(1, BlockSize.S_1024.numberOfBlocksPerMessage(10_000));

        assertEquals(1, BlockSize.S_1024_BERT.numberOfBlocksPerMessage(1024));
        assertEquals(1, BlockSize.S_1024_BERT.numberOfBlocksPerMessage(2000));
        assertEquals(1, BlockSize.S_1024_BERT.numberOfBlocksPerMessage(2047));
        assertEquals(2, BlockSize.S_1024_BERT.numberOfBlocksPerMessage(2048));

        assertEquals(9, BlockSize.S_1024_BERT.numberOfBlocksPerMessage(10_000));
    }

    @Test
    void serializeSize() {
        assertEquals(0, BlockSize.S_16.toRawSzx());
        assertEquals(1, BlockSize.S_32.toRawSzx());
        assertEquals(2, BlockSize.S_64.toRawSzx());
        assertEquals(3, BlockSize.S_128.toRawSzx());
        assertEquals(4, BlockSize.S_256.toRawSzx());
        assertEquals(5, BlockSize.S_512.toRawSzx());
        assertEquals(6, BlockSize.S_1024.toRawSzx());
        assertEquals(7, BlockSize.S_1024_BERT.toRawSzx());
    }
}
