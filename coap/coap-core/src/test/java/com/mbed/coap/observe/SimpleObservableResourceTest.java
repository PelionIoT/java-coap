/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

    @Test
    public void setBodyWith_noObservers() throws CoapException {
        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("", delivListener);

        verify(delivListener).onNoObservers();
        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
    }
}
