/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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