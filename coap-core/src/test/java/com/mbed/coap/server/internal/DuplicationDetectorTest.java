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
import java.util.concurrent.TimeUnit;
import org.junit.Test;

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

    @Test
    public void reduceMapWhenOverSize() throws Exception {
        DuplicationDetector d = new DuplicationDetector(TimeUnit.SECONDS, 10, 100, mock(ScheduledExecutorService.class));

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
