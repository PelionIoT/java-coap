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

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.packet.Opaque.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.observe.ObservableResourceService;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transmission.SingleTimeout;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class ObservationTest {

    private final String RES_OBS_PATH1 = "/obs/path1";
    private CoapServer server;
    private InetSocketAddress SERVER_ADDRESS;
    private ObservableResourceService obsResource;
    private final Opaque token1001 = Opaque.ofBytes(0x10, 0x01);

    @BeforeEach
    public void setUpClass() throws Exception {
        obsResource = new ObservableResourceService(CoapResponse.ok(EMPTY));
        server = CoapServerBuilder.newBuilder().transport(0)
                .route(RouterService.builder()
                        .get("/path1", __ -> completedFuture(CoapResponse.ok("content1")))
                        .get(RES_OBS_PATH1, obsResource)
                )
                .timeout(new SingleTimeout(500)).blockSize(BlockSize.S_128).build();

        server.start();
        SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", server.getLocalSocketAddress().getPort());
    }

    @AfterEach
    public void tearDownClass() throws Exception {
        server.stop();
    }

    @Test
    public void observationTest() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();

        //notify 1
        obsResource.putPayload(of("duupa"));

        CoapResponse packet = obsListener.take();
        assertEquals("duupa", packet.getPayloadString());
        assertEquals(Integer.valueOf(1), packet.options().getObserve());

        //notify 2
        await().until(() ->
                obsResource.putPayload(of("duupa2"))
        );

        packet = obsListener.take();
        assertEquals("duupa2", packet.getPayloadString());
        assertEquals(Integer.valueOf(2), packet.options().getObserve());

        //notify 3 with NON-CONF
        System.out.println("\n-- notify 3 with NON");
        // OBS_RESOURCE_1.setConfirmNotification(false);
        await().until(() ->
                obsResource.putPayload(of("duupa3"))
        );

        packet = obsListener.take();
        assertEquals("duupa3", packet.getPayloadString());
        assertEquals(Integer.valueOf(3), packet.options().getObserve());
        // OBS_RESOURCE_1.setConfirmNotification(true);

        //refresh observation
        await().untilAsserted(() ->
                assertEquals(1, obsResource.observationRelations())
        );
        client.observe(RES_OBS_PATH1, obsListener).get();

        assertEquals(1, obsResource.observationRelations());
        client.close();
    }

    @Test
    public void terminateObservationByServerWithErrorCode() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();

        obsResource.putPayload(of("duupabb"));
        CoapResponse packet = obsListener.take();

        assertEquals("duupabb", packet.getPayloadString());

        await().until(() -> obsResource.terminate(Code.C404_NOT_FOUND));
        CoapResponse terminObserv = obsListener.take();
        assertEquals(Code.C404_NOT_FOUND, terminObserv.getCode());
        assertTrue(terminObserv.options().getObserve() >= 0);
        assertEquals(0, obsResource.observationRelations(), "Number of observation did not change");

        client.close();
        System.out.println("-- END");
    }

    @Test
    public void terminateObservationByServerTimeout() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        SyncObservationListener obsListener = new SyncObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();
        client.close();

        await().until(() ->
                obsResource.putPayload(of("duupabb")) //make notification
        );

        assertEquals(0, obsResource.observationRelations(), "Observation did not terminate");
        System.out.println("\n-- END");
    }

    @Test
    public void dontTerminateObservationIfNoObs() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        SyncObservationListener obsListener = new SyncObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();

        //notify
        obsResource.putPayload(of("keho"));

        //terminate observation by doing get
        client.sendSync(get(RES_OBS_PATH1));

        await().untilAsserted(() ->
                assertEquals(1, obsResource.observationRelations(), "Observation terminated")
        );

        client.close();

        System.out.println("\n-- END");
    }

    @Test
    public void terminateObservationByClientWithRst() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).build();

        //register observation
        client.observe(RES_OBS_PATH1, token1001, obs -> false).get();

        //notify
        obsResource.putPayload(of("keho"));

        await().untilAsserted(() ->
                assertEquals(0, obsResource.observationRelations(), "Observation not terminated")
        );
        assertFalse(obsResource.putPayload(of("keho")));

        client.close();
        System.out.println("\n-- END");
    }

    @Test
    public void observationWithBlocks() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        obsResource.putPayload(ClientServerWithBlocksTest.BIG_RESOURCE);

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_ADDRESS).blockSize(BlockSize.S_128).build();

        //register observation
        SyncObservationListener obsListener = new SyncObservationListener();
        CoapResponse msg = client.observe(RES_OBS_PATH1, obsListener).get();
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE, msg.getPayload());

        //notif 1
        System.out.println("\n-- NOTIF 1");
        obsResource.putPayload(ClientServerWithBlocksTest.BIG_RESOURCE.concat(of("change-1")));
        CoapResponse packet = obsListener.take();
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE.concat(of("change-1")), packet.getPayload());
        //assertEquals(Integer.valueOf(1), packet.headers().getObserve());

        client.close();
        System.out.println("-- END");

    }

    public static class SyncObservationListener implements Function<CoapResponse, Boolean> {

        BlockingQueue<CoapResponse> queue = new LinkedBlockingQueue<>();

        @Override
        public Boolean apply(CoapResponse obs) {
            System.out.println("ADD");
            queue.add(obs);
            return true;
        }

        public CoapResponse take() throws InterruptedException {
            System.out.println("TAKE");
            return queue.poll(5, TimeUnit.SECONDS); // avoid test blocking
            //            return queue.take();
        }

        public CoapResponse take(int timeout, TimeUnit timeUnit) throws InterruptedException {
            return queue.poll(timeout, timeUnit);
        }

    }
}
