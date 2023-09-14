/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Created by szymon
 */
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
}