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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;

public class DuplicationDetectorTest {

    @Test
    public void testDuplicationAfterCleanUpTimeout() throws Exception {
        final int detectionTimeoutMillis = 300;
        final int collectionSize = 1;
        final int cleanupInterval = 100;
        DefaultDuplicateDetectorCache<CoapRequestId, CoapPacket> cache =
                new DefaultDuplicateDetectorCache<>("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupInterval,
                        10_000,
                        Executors.newSingleThreadScheduledExecutor());
        DuplicationDetector detector = new DuplicationDetector(cache);
        cache.start();
        try {
            CoapPacket packet = mock(CoapPacket.class);
            when(packet.getRemoteAddress()).thenReturn(InetSocketAddress.createUnresolved("testHost", 8080));
            when(packet.getMessageId()).thenReturn(9);

            CoapPacket firstIsDuplicated = detector.isMessageRepeated(packet);
            Thread.sleep(detectionTimeoutMillis + cleanupInterval + 10);
            CoapPacket secondIsDuplicated = detector.isMessageRepeated(packet);

            assertNull("insertion to empty duplicate check list fails", firstIsDuplicated);
            assertNull("second insertion after timeout with same id fails", secondIsDuplicated);
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testDuplicationWithinCleanUpTimeout() throws Exception {
        final int detectionTimeoutMillis = 300;
        final int collectionSize = 1;
        final int cleanupInterval = 100;
        DefaultDuplicateDetectorCache<CoapRequestId, CoapPacket> cache =
                new DefaultDuplicateDetectorCache<>("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupInterval,
                        10_000,
                        Executors.newSingleThreadScheduledExecutor());
        DuplicationDetector instance = new DuplicationDetector(cache);
        cache.start();
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
            cache.stop();
        }
    }

    @Test
    public void reduceMapWhenOverSize() throws Exception {
        final int detectionTimeoutMillis = 10_000;
        final int collectionSize = 100;
        final int cleanupIntervalMillis = 10_000;
        final int warnIntervalMillis = 10_000;
        DefaultDuplicateDetectorCache<CoapRequestId, CoapPacket> cache =
                new DefaultDuplicateDetectorCache<>("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupIntervalMillis,
                        warnIntervalMillis,
                        mock(ScheduledExecutorService.class));
        DuplicationDetector d = new DuplicationDetector(cache);

        for (int i = 0; i < 110; i++) {
            CoapPacket req = newCoapPacket(LOCAL_5683).mid(i).con().get().build();
            assertNull(d.isMessageRepeated(req));
            d.putResponse(req, newCoapPacket(LOCAL_5683).mid(i).ack(Code.C205_CONTENT).build());
        }


        int counter = 0;
        for (int i = 0; i < 110; i++) {
            if (d.isMessageRepeated(newCoapPacket(LOCAL_5683).mid(i).con().get().build()) != null) {
                counter++;
            }
        }

        assertEquals(100, counter);
    }
}
