/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.mbed.coap.packet.CoapPacket;
import org.testng.annotations.Test;

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

            assertNull(firstIsDuplicated, "insertion to empty duplicate check list fails");
            assertNull(secondIsDuplicated, "second insertion after timeout with same id fails");
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

            assertNull(firstIsDuplicated, "insertion to empty duplicate check list fails");
            assertNotNull(secondIsDuplicated, "second insertion within timeout with same id succeeds");
        } finally {
            instance.stop();
        }
    }
}
