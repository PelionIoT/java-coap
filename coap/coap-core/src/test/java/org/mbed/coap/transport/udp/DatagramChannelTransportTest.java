/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport.udp;

import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;
import static org.testng.Assert.*;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.utils.FutureCallbackAdapter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
@PrepareForTest({DatagramChannel.class, DatagramChannelTransport.class, DatagramSocket.class})
public class DatagramChannelTransportTest extends PowerMockTestCase {

    @Test
    public void initTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);

        DatagramChannelTransport channel = new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683), true);
        channel.start(mock(TransportReceiver.class));

        channel.stop();
    }

    @Test
    public void dataExchangeTest() throws Exception {
        DatagramChannelTransport transport1 = new DatagramChannelTransport(new InetSocketAddress("localhost", 0), true, false);
        DatagramChannelTransport transport2 = new DatagramChannelTransport(new InetSocketAddress("localhost", 0), true, false);

        TransportReceiver transportReceiver1 = mock(TransportReceiver.class);
        TransportReceiver transportReceiver2 = mock(TransportReceiver.class);

        transport1.start(transportReceiver1);
        transport2.start(transportReceiver2);

        //transport1 --> transport2
        transport1.send("data1_____".getBytes(), 5, new InetSocketAddress("localhost", transport2.getLocalSocketAddress().getPort()), null);
        assertTrue(transport2.performReceive());
        verify(transportReceiver2).onReceive(isA(InetSocketAddress.class), aryEq("data1".getBytes()), eq(TransportContext.NULL));


        //transport1 --> transport2
        transport1.send("data2".getBytes(), 5, new InetSocketAddress("localhost", transport2.getLocalSocketAddress().getPort()), null);
        assertTrue(transport2.performReceive());
        verify(transportReceiver2).onReceive(isA(InetSocketAddress.class), aryEq("data2".getBytes()), eq(TransportContext.NULL));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void initWithIllegalState() throws Exception {
        new DatagramChannelTransport(new InetSocketAddress("localhost", 0), false, true);

    }

    @Test
    public void stopFailTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.isOpen()).thenReturn(Boolean.TRUE);

        DatagramChannelTransport channel = new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683), true);
        channel.start(mock(TransportReceiver.class));
        channel.stop();
        verify(ch).close();
    }

    @Test(expectedExceptions = ClosedChannelException.class)
    public void initFailedTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.configureBlocking(anyBoolean())).thenThrow(new ClosedChannelException());

        CoapServer srv = CoapServerBuilder.newBuilder().transport(new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683))).build();
        srv.start();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void initBindFailedTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        DatagramSocket datSocket = PowerMockito.mock(DatagramSocket.class);

        when(ch.configureBlocking(anyBoolean())).thenReturn(null);
        when(ch.socket()).thenReturn(datSocket);
        doThrow(new BindException()).when(datSocket).bind(isA(InetSocketAddress.class));

        CoapServer srv = CoapServerBuilder.newBuilder().transport(new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683))).build();
        try {
            srv.start();
            fail("Expected: BindException()");
        } catch (IOException ex) {
            //expected
        }
        srv.stop();
    }

    @Test
    public void receiveSendFailTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.receive(isA(ByteBuffer.class))).thenThrow(new IOException());

        doThrow(new IOException()).when(ch).close();
        when(ch.send(isA(ByteBuffer.class), isA(InetSocketAddress.class))).thenThrow(new SecurityException());

        CoapServer srv = CoapServerBuilder.newBuilder().transport(new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683), true)).build();
        srv.start();

        try {
            FutureCallbackAdapter<CoapPacket> fut = new FutureCallbackAdapter<>();
            srv.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", new InetSocketAddress(61616)), fut);
            assertFalse(fut.isDone());

        } catch (CoapException ex) {
            assertEquals(SecurityException.class, ex.getCause().getClass());
        }

        srv.stop();

    }

    @Test
    public void receiveSendFailTest2() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.receive(isA(ByteBuffer.class))).thenReturn(null);

        doThrow(new IOException()).when(ch).close();
        when(ch.send(isA(ByteBuffer.class), isA(InetSocketAddress.class))).thenThrow(new ClosedChannelException());

        CoapServer srv = CoapServerBuilder.newBuilder().transport(new DatagramConnectorChannelMock(ch, new InetSocketAddress(5683), true)).build();
        srv.start();

        try {
            FutureCallbackAdapter<CoapPacket> fut = new FutureCallbackAdapter<>();
            srv.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", new InetSocketAddress(61616)), fut);

        } catch (CoapException ex) {
            //expected
        }

        srv.stop();

    }

    private static class DatagramConnectorChannelMock extends DatagramChannelTransport {

        private DatagramChannel createChannel;
        private boolean skipInit = false;

        public DatagramConnectorChannelMock(DatagramChannel createChannel, InetSocketAddress bindingSocket) {
            super(bindingSocket);
            this.createChannel = createChannel;
            setReuseAddress(false);
        }

        public DatagramConnectorChannelMock(DatagramChannel createChannel, InetSocketAddress bindingSocket, boolean skipInit) {
            super(bindingSocket);
            this.createChannel = createChannel;
            this.skipInit = skipInit;
        }

        @Override
        protected DatagramChannel createChannel() throws IOException {
            return createChannel;
        }

        @Override
        protected void initialize() throws IOException {
            if (!skipInit) {
                super.initialize();
            } else {
                this.channel.set(createChannel);
            }
        }
    }
}
