/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by olesmi01 on 2.10.2015.
 * Tests class for InMemoryTransport class.
 */
@RunWith(value = PowerMockRunner.class)
public class InMemoryTransportTest {

    // http://jira.arm.com/browse/ONSP-917
    @Test
    public void testRandomPortWhenRandomReturnsZeroFirstTime() throws Exception {
        Random mockedRandom = mock(Random.class);
        when(mockedRandom.nextInt(anyInt())).thenReturn(0, 1, 2, 3); // make it return zero at first time
        InMemoryTransport.BindingManager portGenerator = new InMemoryTransport.BindingManager(mockedRandom);
        InetSocketAddress inetSocketAddress = portGenerator.createAddress(0);             // and request to generate random port
        assertNotEquals("Random generated port should NOT be zero", inetSocketAddress.getPort(), 0);
        assertEquals(inetSocketAddress.getPort(), 1); // we use ports in range 1..65535, so when random returns zero - port will be 1
    }


}
