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
package com.mbed.coap.server.internal;

import com.mbed.coap.server.CoapTcpCSMStorage;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by olesmi01 on 27.07.2017.
 * Per-connection CSM storage
 */
public class CoapTcpCSMStorageImpl implements CoapTcpCSMStorage {
    //package local for tests
    final ConcurrentHashMap<InetSocketAddress, CoapTcpCSM> capabilitiesMap = new ConcurrentHashMap<>();
    private final CoapTcpCSM defaultCapability;

    public CoapTcpCSMStorageImpl(CoapTcpCSM defaultCapability) {
        this.defaultCapability = defaultCapability;
    }

    public CoapTcpCSMStorageImpl() {
        this(CoapTcpCSM.BASE);
    }

    @Override
    public void put(InetSocketAddress address, CoapTcpCSM newCapabilities) {
        if (CoapTcpCSM.BASE.equals(newCapabilities)) {
            capabilitiesMap.remove(address);
        } else {
            capabilitiesMap.put(address, newCapabilities);
        }
    }

    @Override
    public CoapTcpCSM getOrDefault(InetSocketAddress address) {
        CoapTcpCSM csm = capabilitiesMap.get(address);
        if (csm == null) {
            return defaultCapability;
        }
        return csm;
    }

    @Override
    public void remove(InetSocketAddress address) {
        capabilitiesMap.remove(address);
    }
}
