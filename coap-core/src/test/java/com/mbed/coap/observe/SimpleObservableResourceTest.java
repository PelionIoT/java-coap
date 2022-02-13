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
package com.mbed.coap.observe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.client.ObservationListener;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.ObservationTest;

/**
 * @author szymon
 */
public class SimpleObservableResourceTest {

    private SimpleObservableResource obsResource;
    private CoapServer server;
    private CoapClient client;

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(new InMemoryCoapTransport(5683))
                .timeout(new SingleTimeout(500)).build();

        obsResource = new SimpleObservableResource("", server);

        server.addRequestHandler("/obs", obsResource);
        server.start();

        client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(5683))
                .transport(new InMemoryCoapTransport())
                .timeout(1000).build();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testDeliveryListener_success() throws CoapException {

        ObservationTest.SyncObservationListener obsListener = new ObservationTest.SyncObservationListener();
        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(3000)).onSuccess(any(InetSocketAddress.class));

        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(3000).times(2)).onSuccess(any(InetSocketAddress.class));

        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();

    }

    @Test
    public void testDeliveryListener_fail() throws CoapException {
        ObservationListener obsListener = mock(ObservationListener.class);
        doThrow(new ObservationTerminatedException(null, null)).when(obsListener).onObservation(any(CoapPacket.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);

        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(3000)).onFail(any(InetSocketAddress.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));
        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(3000).times(2)).onFail(any(InetSocketAddress.class));

        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();
    }

    @Test
    public void testDeliveryListener_successAfterFail() throws CoapException, IllegalStateException, IOException {
        ObservationListener obsListener = mock(ObservationListener.class);
        doThrow(new ObservationTerminatedException(null, null)).doNothing().when(obsListener).onObservation(any(CoapPacket.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);

        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(3000)).onFail(any(InetSocketAddress.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));
        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(3000)).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();

    }

    @Test
    public void testDeliveryListener_timeout() throws CoapException {
        ObservationListener obsListener = mock(ObservationListener.class);

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        client.close();

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);
        verify(delivListener, timeout(3000)).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();
    }

    @Test
    public void setBodyWithNull() throws CoapException {
        assertThrows(NullPointerException.class, () ->
                obsResource.setBody("", null)
        );
    }

    @Test
    public void setBodyInBase64WithNull() throws CoapException {
        assertThrows(NullPointerException.class, () ->
                obsResource.setBody(Opaque.EMPTY, null)
        );
    }

    @Test
    public void testSetBodyAndGetBody() throws CoapException {
        obsResource.setBody("test");
        assertEquals(Opaque.of("test"), obsResource.getBody());
    }

    @Test
    public void setBodyWith_noObservers() throws CoapException {
        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);

        verify(delivListener).onNoObservers();
        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
    }
}
