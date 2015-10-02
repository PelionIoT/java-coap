/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import static org.mockito.Mockito.*;
import org.powermock.modules.testng.PowerMockTestCase;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.util.Random;

/**
 * Created by olesmi01 on 2.10.2015.
 * Tests class for InMemoryTransport class.
 */
public class InMemoryTransportTest extends PowerMockTestCase {

    // http://jira.arm.com/browse/ONSP-917
    @Test
    public void testRandomPortWhenRandomReturnsZeroFirstTime() throws Exception {
        Random mockedRandom = mock(Random.class);
        when(mockedRandom.nextInt(anyInt())).thenReturn(0, 1, 2, 3); // make it return zero at first time
        InMemoryTransport.BindingManager portGenerator = new InMemoryTransport.BindingManager(mockedRandom);
        InetSocketAddress inetSocketAddress = portGenerator.createAddress(0);             // and request to generate random port
        assertNotEquals(inetSocketAddress.getPort(), 0, "Random generated port should NOT be zero");
        assertEquals(inetSocketAddress.getPort(), 1); // we use ports in range 1..65535, so when random returns zero - port will be 1
    }


}
