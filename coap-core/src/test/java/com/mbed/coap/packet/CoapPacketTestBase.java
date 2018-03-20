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
package com.mbed.coap.packet;


import static org.junit.Assert.*;

abstract class CoapPacketTestBase {

    static void assertSimilar(CoapPacket cp1, CoapPacket cp2) {
        assertEquals(cp1.getMethod(), cp2.getMethod());
        assertEquals(cp1.getMessageType(), cp2.getMessageType());
        assertEquals(cp1.getCode(), cp2.getCode());
        assertEquals(cp1.getMessageId(), cp2.getMessageId());

        assertEquals(cp1.headers().getBlock1Req(), cp2.headers().getBlock1Req());
        assertEquals(cp1.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(cp1.headers().getUriPath(), cp2.headers().getUriPath());
        assertEquals(cp1.headers().getUriAuthority(), cp2.headers().getUriAuthority());
        assertEquals(cp1.headers().getUriHost(), cp2.headers().getUriHost());
        assertEquals(cp1.headers().getUriQuery(), cp2.headers().getUriQuery());
        assertEquals(cp1.headers().getLocationPath(), cp2.headers().getLocationPath());
        assertEquals(cp1.headers().getLocationQuery(), cp2.headers().getLocationQuery());

        assertEquals(cp1.headers().getAccept(), cp2.headers().getAccept());
        assertArrayEquals(cp1.headers().getIfMatch(), cp2.headers().getIfMatch());
        assertArrayEquals(cp1.headers().getEtagArray(), cp2.headers().getEtagArray());

        assertEquals(cp1.headers().getIfNonMatch(), cp2.headers().getIfNonMatch());
        assertEquals(cp1.headers().getContentFormat(), cp2.headers().getContentFormat());
        assertArrayEquals(cp1.headers().getEtag(), cp2.headers().getEtag());
        assertEquals(cp1.headers().getMaxAge(), cp2.headers().getMaxAge());
        assertEquals(cp1.headers().getObserve(), cp2.headers().getObserve());
        assertEquals(cp1.headers().getProxyUri(), cp2.headers().getProxyUri());
        assertArrayEquals(cp1.getToken(), cp2.getToken());
        assertEquals(cp1.headers().getUriPort(), cp2.headers().getUriPort());

        assertEquals(cp1.getPayloadString(), cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());

        assertEquals(cp1.getRemoteAddress(), cp2.getRemoteAddress());
    }

}
