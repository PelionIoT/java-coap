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
package com.mbed.coap.server.internal;

import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import org.junit.Test;

/**
 * Created by szymon
 */
public class CoapTcpCSMStorageImplTest {

    @Test
    public void test() {
        CoapTcpCSMStorageImpl capabilities = new CoapTcpCSMStorageImpl();

        assertEquals(CoapTcpCSM.BASE, capabilities.getOrDefault(LOCAL_5683));

        capabilities.put(LOCAL_5683, new CoapTcpCSM(1001, true));

        assertEquals(1001, capabilities.getOrDefault(LOCAL_5683).getMaxMessageSizeInt());
        assertEquals(1001, capabilities.getOrDefault(LOCAL_5683).getMaxMessageSize());

        capabilities.put(LOCAL_5683, CoapTcpCSM.BASE); // remove from storage
        assertNull(capabilities.capabilitiesMap.get(LOCAL_1_5683)); // should be removed

        assertEquals(CoapTcpCSM.BASE, capabilities.getOrDefault(LOCAL_5683));
    }
}