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
package com.mbed.coap.server;

import java.net.InetSocketAddress;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class CoapRequestIdTest {

    CoapRequestId requestId = new CoapRequestId(5000, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId2 = new CoapRequestId(5000, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId3 = new CoapRequestId(5002, new InetSocketAddress("127.0.0.1", 20000));
    CoapRequestId requestId4 = new CoapRequestId(5000, new InetSocketAddress("192.168.0.1", 20000));

    @Test
    public void testGetCreatedTimestampMillis() {
        assert (Math.abs(System.currentTimeMillis() - requestId.getCreatedTimestampMillis()) < 1000);
    }

    @Test
    public void testGetMid() {
        assert (requestId.getMid() == 5000);
    }

    @Test
    public void testGetSourceAddress() {
        assert (requestId.getSourceAddress().equals(new InetSocketAddress("127.0.0.1", 20000)));
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(CoapRequestId.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }
}
