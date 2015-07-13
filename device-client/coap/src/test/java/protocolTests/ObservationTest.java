/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.client.ObservationListener;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationNotEstablishedException;
import org.mbed.coap.exception.ObservationTerminatedException;
import org.mbed.coap.observe.SimpleObservableResource;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.SimpleCoapResource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 * @author szymon
 */
public class ObservationTest {

    private static final String RES_OBS_PATH1 = "/obs/path1";
    private static CoapServer server;
    private static InetSocketAddress SERVER_ADDRESS;
    private static SimpleObservableResource OBS_RESOURCE_1;

    @BeforeClass
    public static void setUpClass() throws Exception {
        server = CoapServerBuilder.newBuilder().transport(0)
                .timeout(new SingleTimeout(500)).blockSize(BlockSize.S_128).build();

        OBS_RESOURCE_1 = new SimpleObservableResource("", server);

        server.addRequestHandler("/path1", new SimpleCoapResource("content1"));
        server.addRequestHandler(RES_OBS_PATH1, OBS_RESOURCE_1);
        server.start();
        SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", server.getLocalSocketAddress().getPort());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        server.stop();
    }

    @Test(expectedExceptions = ObservationNotEstablishedException.class)
    public void observationAttemptOnNonObsResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();
        try {
            client.resource("/path1").sync().observe(null);
        } finally {
            client.close();
        }
    }

    @Test
    public void observationOnNon() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        CoapPacket resp = client.resource(RES_OBS_PATH1).non().sync().observe(obsListener);
        assertEquals(Code.C205_CONTENT, resp.getCode());
        //assertNotNull(resp.headers().getObserve()) ;
    }

    @Test
    public void observationTest() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).observe(obsListener).get();

        //notify 1
        OBS_RESOURCE_1.setBody("duupa");

        CoapPacket packet = obsListener.take();
        assertEquals("duupa", packet.getPayloadString());
        assertEquals(Integer.valueOf(1), packet.headers().getObserve());

        //notify 2
        Thread.sleep(100);
        OBS_RESOURCE_1.setBody("duupa2");

        packet = obsListener.take();
        assertEquals("duupa2", packet.getPayloadString());
        assertEquals(Integer.valueOf(2), packet.headers().getObserve());

        //notify 3 with NON-CONF
        Thread.sleep(100);
        System.out.println("\n-- notify 3 with NON");
        OBS_RESOURCE_1.setConfirmNotification(false);
        OBS_RESOURCE_1.setBody("duupa3");

        packet = obsListener.take();
        assertEquals("duupa3", packet.getPayloadString());
        assertEquals(Integer.valueOf(3), packet.headers().getObserve());
        OBS_RESOURCE_1.setConfirmNotification(true);

        //refresh observation
        int obsNum = OBS_RESOURCE_1.getObservationsAmount();
        client.resource(RES_OBS_PATH1).observe(obsListener);

        assertEquals(obsNum, OBS_RESOURCE_1.getObservationsAmount());
        client.close();
    }

    @Test
    public void terminateObservationByServer() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);

        OBS_RESOURCE_1.setBody("duupabb");
        CoapPacket packet = obsListener.take();

        assertEquals("duupabb", packet.getPayloadString());

        OBS_RESOURCE_1.terminateObservations();
        assertEquals(MessageType.Reset, obsListener.take().getMessageType());
        assertEquals(0, OBS_RESOURCE_1.getObservationsAmount(), "Number of observation did not change");

        client.close();
        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerWithErrorCode() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);

        OBS_RESOURCE_1.setBody("duupabb");
        CoapPacket packet = obsListener.take();

        assertEquals("duupabb", packet.getPayloadString());

        OBS_RESOURCE_1.notifyTermination(Code.C404_NOT_FOUND);
        CoapPacket terminObserv = obsListener.take();
        assertEquals(MessageType.Confirmable, terminObserv.getMessageType());
        assertEquals(Code.C404_NOT_FOUND, terminObserv.getCode());
        assertTrue(terminObserv.headers().getObserve() >= 0);
        assertEquals(0, OBS_RESOURCE_1.getObservationsAmount(), "Number of observation did not change");

        client.close();
        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerTimeout() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);
        client.close();

        final int obsNum = OBS_RESOURCE_1.getObservationsAmount();
        OBS_RESOURCE_1.setBody("duupabb"); //make notification

        //active waiting
        for (int timeout = 10000; timeout > 0 && obsNum == OBS_RESOURCE_1.getObservationsAmount(); timeout -= 100) {
            Thread.sleep(100);
        }

        assertTrue(obsNum > OBS_RESOURCE_1.getObservationsAmount(), "Observation did not terminate");
        System.out.println("\n-- END");
    }

    @Test
    public void dontTerminateObservationIfNoObs() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);

        int obsNum = OBS_RESOURCE_1.getObservationsAmount();
        //notify
        OBS_RESOURCE_1.setBody("keho");

        //terminate observation by doing get
        client.resource(RES_OBS_PATH1).sync().get();

        assertFalse(obsNum > OBS_RESOURCE_1.getObservationsAmount(), "Observation terminated");

        client.close();

        System.out.println("\n-- END");
    }

    @Test
    public void terminateObservationByClientWithRst() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        ObservationListener obsListener = mock(ObservationListener.class);
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);

        int obsNum = OBS_RESOURCE_1.getObservationsAmount();

        //notify
        doThrow(new ObservationTerminatedException(null)).when(obsListener).onObservation(any(CoapPacket.class));
        OBS_RESOURCE_1.setBody("keho");

        Thread.sleep(100);
        assertTrue(obsNum > OBS_RESOURCE_1.getObservationsAmount(), "Observation not terminated");

        client.close();
        System.out.println("\n-- END");
    }

    @Test
    public void observationWithBlocks() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        OBS_RESOURCE_1.setBody(ClientServerWithBlocksTest.BIG_RESOURCE);

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        SyncObservationListener obsListener = new SyncObservationListener();
        CoapPacket msg = client.resource(RES_OBS_PATH1).sync().observe(obsListener);
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE, msg.getPayloadString());

        //notif 1
        System.out.println("\n-- NOTIF 1");
        OBS_RESOURCE_1.setBody(ClientServerWithBlocksTest.BIG_RESOURCE + "change-1");
        CoapPacket packet = obsListener.take();
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE + "change-1", packet.getPayloadString());
        //assertEquals(Integer.valueOf(1), packet.headers().getObserve());

        client.close();
        System.out.println("-- END");

    }

    public static class SyncObservationListener implements ObservationListener {

        BlockingQueue<CoapPacket> queue = new LinkedBlockingQueue<>();

        @Override
        public void onObservation(CoapPacket obsPacket) throws CoapException {
            System.out.println("ADD");
            queue.add(obsPacket);
        }

        public CoapPacket take() throws InterruptedException {
            System.out.println("TAKE");
            return queue.take();
        }

        @Override
        public void onTermination(CoapPacket obsPacket) throws CoapException {
            queue.add(obsPacket);
        }
    }
}
