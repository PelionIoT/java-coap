/**
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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.client.ObservationListener;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.ObservationTest;

/**
 * @author szymon
 */
public class SimpleObservableResourceTest {

    private SimpleObservableResource obsResource;
    private CoapServer server;
    private CoapClient client;

    @Before
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

    @After
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

    @Test(expected = NullPointerException.class)
    public void setBodyWithNull() throws CoapException {
        obsResource.setBody("", null);
    }

    @Test(expected = NullPointerException.class)
    public void setBodyInBase64WithNull() throws CoapException {
        obsResource.setBody(new byte [0], null);
    }

    @Test
    public void testSetBodyAndGetBody() throws CoapException {
        obsResource.setBody("test");
        assertEquals("test", obsResource.getBody());
    }

    @Test
    public void testSetBodyInBytesAndGetBodyBytes() throws CoapException {
        obsResource.setBody(Base64.getDecoder().decode("dGVyY2Vz"));
        assertEquals("terces", obsResource.getBody());
        assert (Arrays.equals(Base64.getDecoder().decode("dGVyY2Vz"), (obsResource.getBodyBytes())));
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
