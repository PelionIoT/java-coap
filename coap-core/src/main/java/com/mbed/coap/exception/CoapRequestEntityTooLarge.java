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
package com.mbed.coap.exception;

import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.Code;

/**
 * Created by szymon
 */
public class CoapRequestEntityTooLarge extends CoapCodeException {

    private final int maxSize;
    private final BlockOption blockOptionHint;

    public CoapRequestEntityTooLarge(int maxSize, String message) {
        super(Code.C413_REQUEST_ENTITY_TOO_LARGE, message);
        this.maxSize = maxSize;
        this.blockOptionHint = null;
    }

    public CoapRequestEntityTooLarge(BlockOption blockOptionHint, String message) {
        super(Code.C413_REQUEST_ENTITY_TOO_LARGE, message);
        this.maxSize = 0;
        this.blockOptionHint = blockOptionHint;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public BlockOption getBlockOptionHint() {
        return blockOptionHint;
    }
}
