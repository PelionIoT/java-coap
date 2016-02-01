/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.client.ObservationListener;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.InMemoryTransport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import protocolTests.ObservationTest;

/**
 * @author szymon
 */
public class SimpleObservableResourceTest {

    private SimpleObservableResource obsResource;
    private CoapServer server;
    private CoapClient client;

    @BeforeMethod
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(new InMemoryTransport(5683))
                .timeout(new SingleTimeout(500)).build();

        obsResource = new SimpleObservableResource("", server);

        server.addRequestHandler("/obs", obsResource);
        server.start();

        client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(new InMemoryTransport())
                .timeout(1000).build();
    }

    @AfterMethod
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testDeliveryListener_success() throws CoapException {

        ObservationTest.SyncObservationListener obsListener = new ObservationTest.SyncObservationListener();
        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);
        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(1000)).onSuccess(any(InetSocketAddress.class));

        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(1000).times(2)).onSuccess(any(InetSocketAddress.class));

        verify(delivListener, never()).onFail(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();

    }

    @Test
    public void testDeliveryListener_fail() throws CoapException {
        ObservationListener obsListener = mock(ObservationListener.class);
        doThrow(new ObservationTerminatedException(null)).when(obsListener).onObservation(any(CoapPacket.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);

        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(1000)).onFail(any(InetSocketAddress.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));
        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(1000).times(2)).onFail(any(InetSocketAddress.class));

        verify(delivListener, never()).onSuccess(any(InetSocketAddress.class));
        verify(delivListener, never()).onNoObservers();
    }

    @Test
    public void testDeliveryListener_successAfterFail() throws CoapException, IllegalStateException, IOException {
        ObservationListener obsListener = mock(ObservationListener.class);
        doThrow(new ObservationTerminatedException(null)).doNothing().when(obsListener).onObservation(any(CoapPacket.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));

        NotificationDeliveryListener delivListener = mock(NotificationDeliveryListener.class);

        obsResource.setBody("A", delivListener);
        verify(delivListener, timeout(1000)).onFail(any(InetSocketAddress.class));

        assertNotNull(client.resource("/obs").sync().observe(obsListener));
        obsResource.setBody("B", delivListener);
        verify(delivListener, timeout(1000)).onSuccess(any(InetSocketAddress.class));
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

    @Test(expectedExceptions = NullPointerException.class)
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
