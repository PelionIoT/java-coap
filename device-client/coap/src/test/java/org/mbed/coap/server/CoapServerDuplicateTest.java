package org.mbed.coap.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.test.StubCoapServer;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.transport.TransportWorkerWrapper;

/**
 *
 * @author szymon
 */
public class CoapServerDuplicateTest {

    StubCoapServer coapServer;
    CountDownLatch latch;

    @Before
    public void setUp() throws IOException {
        CoapServer server = CoapServerBuilder.newBuilder().transport(new InMemoryTransport(5683)).build();
        server.setErrorCallback(new CoapErrorCallback() {
            @Override
            public void parserError(byte[] packet, CoapException exception) {
            }

            @Override
            public void duplicated(CoapPacket request) {
                if (latch != null) {
                    latch.countDown();
                }
            }
        });

        coapServer = new StubCoapServer(server);
        coapServer.start();
        coapServer.whenPUT("/test").thenReturn("dupa");
        coapServer.whenPOST("/test-delay").delay(10000).thenReturn("dupa3");
        coapServer.enableObservationHandler();

    }

    @After
    public void tearDown() {
        coapServer.stop();
    }

    @Test
    public void testDuplicateRequest() throws IOException, CoapException, InterruptedException {
        latch = new CountDownLatch(1);

        ConnectorMockCoap connectorMock = new ConnectorMockCoap();
        new TransportWorkerWrapper(connectorMock).start(connectorMock);
        CoapPacket req = new CoapPacket(Method.PUT, MessageType.Confirmable, "/test", coapServer.getAddress());

        connectorMock.send(req);
        CoapPacket resp = connectorMock.receiveQueue.poll(10, TimeUnit.SECONDS);
        assertEquals("dupa", resp.getPayloadString());
        assertNotNull(coapServer.verifyPUT("/test"));

        //repeated request
        coapServer.reset();
        Thread.sleep(20);
        connectorMock.send(req);
        resp = connectorMock.receiveQueue.poll(1, TimeUnit.SECONDS);
        assertEquals("dupa", resp.getPayloadString());
        assertNull(coapServer.verifyPUT("/test"));
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDuplicateRequestNotProcessed() throws IOException, CoapException, InterruptedException {
        latch = new CountDownLatch(1);

        ConnectorMockCoap connectorMock = new ConnectorMockCoap();
        new TransportWorkerWrapper(connectorMock).start(connectorMock);
        CoapPacket req = new CoapPacket(Method.POST, MessageType.Confirmable, "/test-delay", coapServer.getAddress());

        connectorMock.send(req);
        CoapPacket resp = connectorMock.receiveQueue.poll(500, TimeUnit.MILLISECONDS);
        assertNull(resp);

        //repeaded request
        System.out.println("second request");
        connectorMock.send(req);
        resp = connectorMock.receiveQueue.poll(500, TimeUnit.MILLISECONDS);
        assertNull(resp);

        //let response be send
        System.out.println("let response be send");
        synchronized (coapServer) {
            coapServer.notify();
        }

        resp = connectorMock.receiveQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertEquals("dupa3", resp.getPayloadString());
        //assertNull(coapServer.verifyPUT("/test"));
        assertTrue("unexpected messages", connectorMock.receiveQueue.isEmpty());
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDuplicateNotification() throws IOException, CoapException, InterruptedException {
        latch = new CountDownLatch(1);

        ConnectorMockCoap connectorMock = new ConnectorMockCoap();
        new TransportWorkerWrapper(connectorMock).start(connectorMock);
        CoapPacket notif = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, coapServer.getAddress());
        notif.setMessageId(12);
        notif.setToken(HeaderOptions.convertVariableUInt(1234));
        notif.headers().setObserve(1);
        notif.setPayload("dupa2");

        connectorMock.send(notif);
        CoapPacket resp = connectorMock.receiveQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(MessageType.Acknowledgement, resp.getMessageType());
        assertNotNull(coapServer.getNotifQueue().poll(10, TimeUnit.SECONDS));

        //repeated request
        coapServer.reset();
        connectorMock.send(notif);
        resp = connectorMock.receiveQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(MessageType.Acknowledgement, resp.getMessageType());
        assertNull("received notification from retransmission", coapServer.getNotifQueue().peek());
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    private static class ConnectorMockCoap extends InMemoryTransport implements TransportReceiver {

        public BlockingQueue<CoapPacket> receiveQueue = new LinkedBlockingQueue<>();

        public ConnectorMockCoap() {
            //start(this);
        }

        public void send(CoapPacket packet) throws CoapException, IOException {
            send(packet.toByteArray(), packet.toByteArray().length, packet.getRemoteAddress(), TransportContext.NULL);
        }

        @Override
        public void onReceive(InetSocketAddress adr, ByteBuffer buffer, TransportContext transportContext) {
            try {
                CoapPacket p = CoapPacket.read(buffer.array(), buffer.position(), null);
                receiveQueue.add(p);
            } catch (CoapException ex) {
                ex.printStackTrace();
            }
        }
    }
}
