/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
package protocolTests;

import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.client.ObservationListener;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.observe.SimpleObservableResource;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class ObservationTest {

    private final String RES_OBS_PATH1 = "/obs/path1";
    private CoapServer server;
    private InetSocketAddress SERVER_ADDRESS;
    private SimpleObservableResource OBS_RESOURCE_1;

    @BeforeEach
    public void setUpClass() throws Exception {
        server = CoapServerBuilder.newBuilder().transport(0)
                .timeout(new SingleTimeout(500)).blockSize(BlockSize.S_128).build();

        OBS_RESOURCE_1 = new SimpleObservableResource("", server);

        server.addRequestHandler("/path1", new ReadOnlyCoapResource("content1"));
        server.addRequestHandler(RES_OBS_PATH1, OBS_RESOURCE_1);
        server.start();
        SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", server.getLocalSocketAddress().getPort());
    }

    @AfterEach
    public void tearDownClass() throws Exception {
        server.stop();
    }

    @Test
    public void observationAttemptOnNonObsResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();
        assertThrows(ObservationNotEstablishedException.class, () ->
                client.resource("/path1").sync().observe(null)
        );
        client.close();
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
        await().untilAsserted(() ->
                assertEquals(1, OBS_RESOURCE_1.getObservationsAmount())
        );
        client.resource(RES_OBS_PATH1).observe(obsListener).get();

        assertEquals(1, OBS_RESOURCE_1.getObservationsAmount());
        client.close();
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

        OBS_RESOURCE_1.terminateObservations(Code.C404_NOT_FOUND);
        CoapPacket terminObserv = obsListener.take();
        assertEquals(MessageType.Confirmable, terminObserv.getMessageType());
        assertEquals(Code.C404_NOT_FOUND, terminObserv.getCode());
        assertTrue(terminObserv.headers().getObserve() >= 0);
        assertEquals(0, OBS_RESOURCE_1.getObservationsAmount(), "Number of observation did not change");

        client.close();
        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerWithOkCode() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());

        assertThrows(IllegalArgumentException.class, () ->
                OBS_RESOURCE_1.terminateObservations(Code.C204_CHANGED)
        );

        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerWithoutErrorCode() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());

        assertThrows(IllegalArgumentException.class, () ->
                OBS_RESOURCE_1.terminateObservations(null)
        );

        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerTimeout() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);
        client.close();

        OBS_RESOURCE_1.setBody("duupabb"); //make notification

        await().untilAsserted(() ->
                assertEquals(0, OBS_RESOURCE_1.getObservationsAmount(), "Observation did not terminate")
        );
        System.out.println("\n-- END");
    }

    @Test
    public void dontTerminateObservationIfNoObs() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        SyncObservationListener obsListener = new SyncObservationListener();
        client.resource(RES_OBS_PATH1).sync().observe(obsListener);

        //notify
        OBS_RESOURCE_1.setBody("keho");

        //terminate observation by doing get
        client.resource(RES_OBS_PATH1).sync().get();

        await().untilAsserted(() ->
                assertEquals(1, OBS_RESOURCE_1.getObservationsAmount(), "Observation terminated")
        );

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
        doThrow(new ObservationTerminatedException(null, null)).when(obsListener).onObservation(any(CoapPacket.class));
        OBS_RESOURCE_1.setBody("keho");

        await().untilAsserted(() ->
                assertTrue(obsNum > OBS_RESOURCE_1.getObservationsAmount(), "Observation not terminated")
        );

        client.close();
        System.out.println("\n-- END");
    }

    @Test
    public void observationWithBlocks() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        OBS_RESOURCE_1.setBody(ClientServerWithBlocksTest.BIG_RESOURCE);

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).blockSize(BlockSize.S_128).build();

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
            return queue.poll(5, TimeUnit.SECONDS); // avoid test blocking
            //            return queue.take();
        }

        public CoapPacket take(int timeout, TimeUnit timeUnit) throws InterruptedException {
            return queue.poll(timeout, timeUnit);
        }

        @Override
        public void onTermination(CoapPacket obsPacket) throws CoapException {
            queue.add(obsPacket);
        }
    }
}
