/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.Opaque.EMPTY;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.transport.udp.DatagramSocketTransport.udp;
import static com.mbed.coap.transmission.RetransmissionBackOff.ofFixed;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.ObservableResourceService;
import com.mbed.coap.server.RouterService;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.ObservationListener;


public class ObservationTest {

    private final String RES_OBS_PATH1 = "/obs/path1";
    private CoapServer server;
    private InetSocketAddress SERVER_ADDRESS;
    private ObservableResourceService obsResource;
    private final Opaque token1001 = Opaque.ofBytes(0x10, 0x01);

    @BeforeEach
    public void setUpClass() throws Exception {
        obsResource = new ObservableResourceService(CoapResponse.ok(EMPTY));
        server = CoapServer.builder().transport(udp())
                .route(RouterService.builder()
                        .get("/path1", __ -> completedFuture(CoapResponse.ok("content1")))
                        .get(RES_OBS_PATH1, obsResource)
                )
                .retransmission(ofFixed(ofMillis(500))).blockSize(BlockSize.S_128).build();

        server.start();
        SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", server.getLocalSocketAddress().getPort());
    }

    @AfterEach
    public void tearDownClass() throws Exception {
        server.stop();
    }

    @Test
    public void observationTest() throws Exception {
        CoapClient client = CoapServer.builder().transport(udp()).buildClient(SERVER_ADDRESS);

        ObservationListener obsListener = new ObservationListener();
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
        CoapClient client = CoapServer.builder().transport(udp()).buildClient(SERVER_ADDRESS);

        ObservationListener obsListener = new ObservationListener();
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
    }

    @Test
    public void terminateObservationByServerTimeout() throws Exception {
        CoapClient client = CoapServer.builder().transport(udp()).buildClient(SERVER_ADDRESS);

        ObservationListener obsListener = new ObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();
        client.close();

        await().until(() ->
                obsResource.putPayload(of("duupabb")) //make notification
        );

        assertEquals(0, obsResource.observationRelations(), "Observation did not terminate");
    }

    @Test
    public void dontTerminateObservationIfNoObs() throws Exception {
        CoapClient client = CoapServer.builder().transport(udp()).buildClient(SERVER_ADDRESS);

        //register observation
        ObservationListener obsListener = new ObservationListener();
        client.observe(RES_OBS_PATH1, token1001, obsListener).get();

        //notify
        obsResource.putPayload(of("keho"));

        //terminate observation by doing get
        client.sendSync(get(RES_OBS_PATH1));

        await().untilAsserted(() ->
                assertEquals(1, obsResource.observationRelations(), "Observation terminated")
        );

        client.close();
    }

    @Test
    public void terminateObservationByClientWithRst() throws Exception {
        CoapClient client = CoapServer.builder().transport(udp()).buildClient(SERVER_ADDRESS);

        //register observation
        client.observe(RES_OBS_PATH1, token1001, obs -> false).get();

        //notify
        obsResource.putPayload(of("keho"));

        await().untilAsserted(() ->
                assertEquals(0, obsResource.observationRelations(), "Observation not terminated")
        );
        assertFalse(obsResource.putPayload(of("keho")));

        client.close();
    }

    @Test
    public void observationWithBlocks() throws Exception {
        obsResource.putPayload(ClientServerWithBlocksTest.BIG_RESOURCE);

        CoapClient client = CoapServer.builder().transport(udp()).blockSize(BlockSize.S_128).buildClient(SERVER_ADDRESS);

        //register observation
        ObservationListener obsListener = new ObservationListener();
        CoapResponse msg = client.observe(RES_OBS_PATH1, obsListener).get();
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE, msg.getPayload());

        //notif 1
        System.out.println("\n-- NOTIF 1");
        obsResource.putPayload(ClientServerWithBlocksTest.BIG_RESOURCE.concat(of("change-1")));
        CoapResponse packet = obsListener.take();
        assertEquals(ClientServerWithBlocksTest.BIG_RESOURCE.concat(of("change-1")), packet.getPayload());
        //assertEquals(Integer.valueOf(1), packet.headers().getObserve());

        client.close();
    }

}
