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
package com.mbed.coap.server;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class MessageIdSupplierImpl implements MessageIdSupplier {

    private final AtomicInteger globalMid;

    public MessageIdSupplierImpl() {
        this(new Random().nextInt(0xFFFF));
    }

    public MessageIdSupplierImpl(int initMid) {
        this.globalMid = new AtomicInteger(initMid);
    }

    @Override
    public int getNextMID() {
        return 0xFFFF & (globalMid.incrementAndGet());
    }
}
