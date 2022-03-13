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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

public class DefaultDuplicateDetectorCacheTest {

    @Test
    public void testDuplicationAfterCleanUpTimeout() throws Exception {
        final int detectionTimeoutMillis = 300;
        final int collectionSize = 1;
        final int cleanupInterval = 100;
        DefaultDuplicateDetectorCache cache =
                new DefaultDuplicateDetectorCache("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupInterval,
                        10_000,
                        Executors.newSingleThreadScheduledExecutor());

        try {
            CoapRequestId requestId = new CoapRequestId(9, InetSocketAddress.createUnresolved("testHost", 8080));

            CoapPacket firstIsDuplicated = cache.putIfAbsent(requestId, mock(CoapPacket.class));
            Thread.sleep(detectionTimeoutMillis + cleanupInterval + 10);
            CoapPacket secondIsDuplicated = cache.putIfAbsent(requestId, mock(CoapPacket.class));

            assertNull(firstIsDuplicated, "insertion to empty duplicate check list fails");
            assertNull(secondIsDuplicated, "second insertion after timeout with same id fails");
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testDuplicationWithinCleanUpTimeout() throws Exception {
        final int detectionTimeoutMillis = 300;
        final int collectionSize = 1;
        final int cleanupInterval = 100;
        DefaultDuplicateDetectorCache cache =
                new DefaultDuplicateDetectorCache("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupInterval,
                        10_000,
                        Executors.newSingleThreadScheduledExecutor());

        try {
            CoapRequestId requestId = new CoapRequestId(9, InetSocketAddress.createUnresolved("testHost", 8080));

            CoapPacket firstIsDuplicated = cache.putIfAbsent(requestId, mock(CoapPacket.class));
            Thread.sleep(cleanupInterval + 1);
            CoapPacket secondIsDuplicated = cache.putIfAbsent(requestId, mock(CoapPacket.class));

            assertNull(firstIsDuplicated, "insertion to empty duplicate check list fails");
            assertNotNull(secondIsDuplicated, "second insertion within timeout with same id succeeds");
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
        DefaultDuplicateDetectorCache cache =
                new DefaultDuplicateDetectorCache("Default cache",
                        collectionSize,
                        detectionTimeoutMillis,
                        cleanupIntervalMillis,
                        warnIntervalMillis,
                        mock(ScheduledExecutorService.class));

        for (int i = 0; i < 110; i++) {
            CoapRequestId requestId = new CoapRequestId(i, LOCAL_5683);
            assertNull(cache.putIfAbsent(requestId, mock(CoapPacket.class)));
            cache.put(requestId, newCoapPacket(LOCAL_5683).mid(i).ack(Code.C205_CONTENT).build());
        }


        int counter = 0;
        for (int i = 0; i < 110; i++) {
            CoapRequestId requestId = new CoapRequestId(i, LOCAL_5683);
            if (cache.putIfAbsent(requestId, mock(CoapPacket.class)) != null) {
                counter++;
            }
        }

        assertEquals(100, counter);
    }
}
