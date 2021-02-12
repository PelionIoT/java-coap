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
package com.mbed.coap.server;

import static com.mbed.coap.server.CoapServerBuilder.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.internal.CoapRequestId;
import com.mbed.coap.server.internal.CoapUdpMessaging;
import com.mbed.coap.utils.CacheImpl;
import java.net.InetAddress;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;

/**
 * Created by szymon
 */
public class CoapServerBuilderTest {

    @Test
    public void scheduleExecutor() throws Exception {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);

        CoapServer server = newBuilder().transport(0).scheduledExecutor(scheduledExecutorService).build();

        assertEquals(scheduledExecutorService, ((CoapUdpMessaging) server.getCoapMessaging()).getScheduledExecutor());
    }

    @Test
    public void scheduleExecutorWithPort() throws Exception {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);

        CoapServer server = newBuilder().transport(InetAddress.getByName("localhost"), 0).scheduledExecutor(scheduledExecutorService).build();

        assertEquals(scheduledExecutorService, ((CoapUdpMessaging) server.getCoapMessaging()).getScheduledExecutor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_illegal_duplicateMsgCacheSize() throws Exception {
        newBuilder().duplicateMsgCacheSize(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_illegal_duplicateMsgCleanIntervalInMillis() throws Exception {
        newBuilder().duplicateMsgCleanIntervalInMillis(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_illegal_duplicateMsgWarningMessageIntervalInMillis() throws Exception {
        newBuilder().duplicateMsgWarningMessageIntervalInMillis(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_illegal_duplicateMsgDetectionTimeInMillis() throws Exception {
        newBuilder().duplicateMsgDetectionTimeInMillis(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void usingCustomCacheWithoutTransport() throws Exception {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        CacheImpl<CoapRequestId, CoapPacket> cache = new CacheImpl<>("testCache", 100, 120_1000, 10_000, scheduledExecutorService);
        newBuilder().duplicateMessageDetectorCache(cache).build();

    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_missingTransport() throws Exception {
        newBuilder().build();
    }

    @Test(expected = NullPointerException.class)
    public void shouldFail_whenMissingDuplicateCallback() throws Exception {
        newBuilder().duplicatedCoapMessageCallback(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_whenIllegalTimeoutValue() throws Exception {
        newBuilder().delayedTimeout(-1L);
    }
}
