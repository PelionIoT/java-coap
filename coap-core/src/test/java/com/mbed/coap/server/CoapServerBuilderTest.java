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
package com.mbed.coap.server;

import static com.mbed.coap.server.CoapServerBuilder.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.server.internal.CoapServerForUdp;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;

/**
 * Created by szymon
 */
public class CoapServerBuilderTest {

    @Test
    public void scheduleExecutor() throws Exception {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);

        CoapServerForUdp server = newBuilder().transport(0).scheduledExecutor(scheduledExecutorService).build();

        assertEquals(scheduledExecutorService, server.getScheduledExecutor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFail_when_illegal_duplicateMsgCacheSize() throws Exception {
        newBuilder().duplicateMsgCacheSize(0);
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