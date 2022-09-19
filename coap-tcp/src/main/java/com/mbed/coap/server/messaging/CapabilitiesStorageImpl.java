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
package com.mbed.coap.server.messaging;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-connection CSM storage
 */
public class CapabilitiesStorageImpl implements CapabilitiesStorage {
    //package local for tests
    final ConcurrentHashMap<InetSocketAddress, Capabilities> capabilitiesMap = new ConcurrentHashMap<>();
    private final Capabilities defaultCapability;

    public CapabilitiesStorageImpl(Capabilities defaultCapability) {
        this.defaultCapability = defaultCapability;
    }

    public CapabilitiesStorageImpl() {
        this(Capabilities.BASE);
    }

    @Override
    public void put(InetSocketAddress address, Capabilities newCapabilities) {
        if (Capabilities.BASE.equals(newCapabilities)) {
            capabilitiesMap.remove(address);
        } else {
            capabilitiesMap.put(address, newCapabilities);
        }
    }

    @Override
    public Capabilities getOrDefault(InetSocketAddress address) {
        return capabilitiesMap.getOrDefault(address, defaultCapability);
    }

    @Override
    public void remove(InetSocketAddress address) {
        capabilitiesMap.remove(address);
    }
}
