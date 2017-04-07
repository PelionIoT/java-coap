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
import static org.mockito.Mockito.*;
import com.mbed.coap.packet.CoapPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * @author user
 */
public class DuplicationDetectorTest {

    @Test
    public void testDuplicationAfterCleanUpTimeout() throws Exception {
        final int timeout = 300;
        final int collectionSize = 1;
        DuplicationDetector detector = new DuplicationDetector(TimeUnit.MILLISECONDS, timeout, collectionSize, Executors.newSingleThreadScheduledExecutor());
        final int cleanupInterval = 100;
        detector.setCleanDelayMili(cleanupInterval);
        detector.start();
        try {
            CoapPacket packet = mock(CoapPacket.class);
            when(packet.getRemoteAddress()).thenReturn(InetSocketAddress.createUnresolved("testHost", 8080));
            when(packet.getMessageId()).thenReturn(9);

            CoapPacket firstIsDuplicated = detector.isMessageRepeated(packet);
            Thread.sleep(timeout + cleanupInterval + 10);
            CoapPacket secondIsDuplicated = detector.isMessageRepeated(packet);

            assertNull("insertion to empty duplicate check list fails", firstIsDuplicated);
            assertNull("second insertion after timeout with same id fails", secondIsDuplicated);
        } finally {
            detector.stop();
        }
    }

    @Test
    public void testDuplicationWithinCleanUpTimeout() throws Exception {
        final int timeout = 300;
        final int collectionSize = 1;
        DuplicationDetector instance = new DuplicationDetector(TimeUnit.MILLISECONDS, timeout, collectionSize, Executors.newSingleThreadScheduledExecutor());
        final int cleanupInterval = 100;
        instance.setCleanDelayMili(cleanupInterval);
        instance.start();
        try {
            CoapPacket packet = mock(CoapPacket.class);
            when(packet.getRemoteAddress()).thenReturn(InetSocketAddress.createUnresolved("testHost", 8080));
            when(packet.getMessageId()).thenReturn(9);

            CoapPacket firstIsDuplicated = instance.isMessageRepeated(packet);
            Thread.sleep(cleanupInterval + 1);
            CoapPacket secondIsDuplicated = instance.isMessageRepeated(packet);

            assertNull("insertion to empty duplicate check list fails", firstIsDuplicated);
            assertNotNull("second insertion within timeout with same id succeeds", secondIsDuplicated);
        } finally {
            instance.stop();
        }
    }
}
