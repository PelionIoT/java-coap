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

import static com.mbed.coap.server.CoapServerBuilder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;


public class CoapServerBuilderTest {

    @Test
    public void shouldFail_when_illegal_duplicateMsgCacheSize() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                newBuilder().duplicateMsgCacheSize(0)
        );
    }

    @Test
    public void shouldFail_when_illegal_duplicateMsgCleanIntervalInMillis() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                newBuilder().duplicateMsgCleanIntervalInMillis(0)
        );
    }

    @Test
    public void shouldFail_when_illegal_duplicateMsgWarningMessageIntervalInMillis() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                newBuilder().duplicateMsgWarningMessageIntervalInMillis(0)
        );
    }

    @Test
    public void shouldFail_when_illegal_duplicateMsgDetectionTimeInMillis() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                newBuilder().duplicateMsgDetectionTimeInMillis(0)
        );
    }

    @Test
    public void usingCustomCacheWithoutTransport() throws Exception {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        DefaultDuplicateDetectorCache cache =
                new DefaultDuplicateDetectorCache("testCache", 100, 120_1000, 10_000, 10_000, scheduledExecutorService);
        assertThrows(NullPointerException.class, () ->
                newBuilder().duplicateMessageDetectorCache(cache).build()
        );
    }

    @Test
    public void shouldFail_when_missingTransport() throws Exception {
        assertThrows(NullPointerException.class, () ->
                newBuilder().build()
        );
    }

    @Test
    public void shouldFail_whenMissingDuplicateCallback() throws Exception {
        assertThrows(NullPointerException.class, () ->
                newBuilder().duplicatedCoapMessageCallback(null)
        );
    }

    @Test
    public void shouldFail_whenIllegalTimeoutValue() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                newBuilder().finalTimeout(Duration.ofMillis(-1))
        );
    }
}
