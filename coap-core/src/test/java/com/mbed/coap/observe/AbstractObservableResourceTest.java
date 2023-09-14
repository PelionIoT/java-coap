/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package com.mbed.coap.observe;

import static org.mockito.Mockito.*;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by szymon.
 */
public class AbstractObservableResourceTest {

    private CoapServer mockServer;
    private NotificationDeliveryListener listener;
    private static final InetSocketAddress ADDRESS = InMemoryCoapTransport.createAddress(5683);

    @Before
    public void setUp() throws Exception {
        mockServer = mock(CoapServer.class);
        listener = mock(NotificationDeliveryListener.class);
    }

    @Test
    public void shouldCallOnFail_whenNotificationNotSend() throws Exception {
        AbstractObservableResource obsResource = new SimpleObservableResource("test", mockServer);

        ObservationRelation observationRelation = new ObservationRelation("12".getBytes(), ADDRESS, 1, true);
        observationRelation.setIsDelivering(true);
        obsResource.addObservationRelation(observationRelation, "/test");

        obsResource.notifyChange("d".getBytes(), null, null, null, listener);
        verify(listener).onFail(eq(ADDRESS));
    }

}