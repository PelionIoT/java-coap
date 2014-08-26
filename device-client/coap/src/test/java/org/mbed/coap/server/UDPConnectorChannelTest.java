package org.mbed.coap.server;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.udp.DatagramChannelTransport;
import org.mbed.coap.utils.FutureCallbackAdapter;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author szymon
 */
@RunWith(value = PowerMockRunner.class)

@PrepareForTest({DatagramChannel.class, DatagramChannelTransport.class, DatagramSocket.class})
public class UDPConnectorChannelTest {

    @Test
    public void initTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);

        DatagramChannelTransport channel = new UDPConnectorChannelMock(ch, new InetSocketAddress(5683), true);
        channel.start(mock(TransportReceiver.class));

        channel.stop();

    }

    @Test
    public void stopFailTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.isOpen()).thenReturn(Boolean.TRUE);

        DatagramChannelTransport channel = new UDPConnectorChannelMock(ch, new InetSocketAddress(5683), true);
        channel.start(mock(TransportReceiver.class));
        channel.stop();
        verify(ch).close();
    }

    @Test(expected = ClosedChannelException.class)
    public void initFailedTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        when(ch.configureBlocking(anyBoolean())).thenThrow(new ClosedChannelException());

        CoapServer srv = CoapServer.newBuilder().transport(new UDPConnectorChannelMock(ch, new InetSocketAddress(5683))).build();
        srv.start();
    }

    @Test(expected = IllegalStateException.class)
    public void initBindFailedTest() throws IOException {
        DatagramChannel ch = PowerMockito.mock(DatagramChannel.class);
        DatagramSocket datSocket = PowerMockito.mock(DatagramSocket.class);

        when(ch.configureBlocking(anyBoolean())).thenReturn(null);
        when(ch.socket()).thenReturn(datSocket);
        doThrow(new BindException()).when(datSocket).bind(isA(InetSocketAddress.class));

        CoapServer srv = CoapServer.newBuilder().transport(new UDPConnectorChannelMock(ch, new InetSocketAddress(5683))).build();
        try {
            srv.start();
            Assert.fail("Expected: BindException()");
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

        CoapServer srv = CoapServer.newBuilder().transport(new UDPConnectorChannelMock(ch, new InetSocketAddress(5683), true)).build();
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

        CoapServer srv = CoapServer.newBuilder().transport(new UDPConnectorChannelMock(ch, new InetSocketAddress(5683), true)).build();
        srv.start();

        try {
            FutureCallbackAdapter<CoapPacket> fut = new FutureCallbackAdapter<>();
            srv.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", new InetSocketAddress(61616)), fut);

        } catch (CoapException ex) {
            //expected
        }

        srv.stop();

    }

    private static class UDPConnectorChannelMock extends DatagramChannelTransport {

        private DatagramChannel createChannel;
        private boolean skipInit = false;

        public UDPConnectorChannelMock(DatagramChannel createChannel, InetSocketAddress bindingSocket) {
            super(bindingSocket);
            this.createChannel = createChannel;
            setReuseAddress(false);
        }

        public UDPConnectorChannelMock(DatagramChannel createChannel, InetSocketAddress bindingSocket, boolean skipInit) {
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
                this.channel = createChannel;
            }
        }
    }
}
