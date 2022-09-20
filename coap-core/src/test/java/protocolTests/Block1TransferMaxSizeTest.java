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
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.ObservableResourceService;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.utils.ObservationConsumer;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Block1 header option block transfer size limit tests.
 */
public class Block1TransferMaxSizeTest {
    private ChangeableResource changeableResource;
    private ObservableResourceService observableResource;

    private static final Opaque OBS_RESOURCE_INIT_VALUE = Opaque.of("0_2345678901234|");

    private int SERVER_PORT;
    private static final String CHANGEABLE_RESOURCE_PATH = "/test/res";
    private static final String OBSERVABLE_RESOURCE_PATH = "/test/obs";
    private static final int MAX_DATA = 32;

    private CoapServer server = null;
    private CoapClient client = null;

    @BeforeEach
    public void setUp() throws IOException {

        changeableResource = new ChangeableResource();
        observableResource = new ObservableResourceService(CoapResponse.ok(OBS_RESOURCE_INIT_VALUE));

        server = CoapServerBuilder.newBuilder()
                .route(RouterService.builder()
                        .get(CHANGEABLE_RESOURCE_PATH, changeableResource)
                        .put(CHANGEABLE_RESOURCE_PATH, changeableResource)
                        .get(OBSERVABLE_RESOURCE_PATH, observableResource)
                )
                .maxIncomingBlockTransferSize(MAX_DATA)
                .blockSize(BlockSize.S_16)
                .transport(0)
                .build();

        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();


        client = CoapClientBuilder.newBuilder(SERVER_PORT)
                .maxIncomingBlockTransferSize(MAX_DATA)
                .blockSize(BlockSize.S_16)
                .build();
    }

    @AfterEach
    public void tearDown() throws IOException {
        client.close();
        server.stop();
    }

    @Test
    public void testBlock1WorksFineBelowLimit() throws CoapException, ExecutionException, InterruptedException {
        assertEquals(ChangeableResource.INIT_DATA, changeableResource.data);

        Opaque payload = of("0_2345678901234|1_2345678901234|");
        CoapResponse msg = client.sendSync(put(CHANGEABLE_RESOURCE_PATH).blockSize(BlockSize.S_16).payload(payload));

        assertEquals(Code.C204_CHANGED, msg.getCode());
        assertEquals(payload, changeableResource.data);
        assertEquals(new BlockOption(1, BlockSize.S_16, false), msg.options().getBlock1Req());
    }

    @Test
    public void testBlock1ErrorTooLargeEntity_oneMorePacket() throws CoapException {
        assertEquals(ChangeableResource.INIT_DATA, changeableResource.data);

        String payload = "0_2345678901234|1_2345678901234|2";
        CoapResponse msg = client.sendSync(put(CHANGEABLE_RESOURCE_PATH).payload(payload));

        assertEquals(Code.C413_REQUEST_ENTITY_TOO_LARGE, msg.getCode());
        assertEquals(ChangeableResource.INIT_DATA, changeableResource.data);
        // should report maximum allowed data size in Size1 header
        assertEquals(Integer.valueOf(MAX_DATA), msg.options().getSize1());
    }

    @Test
    public void testBlock1ErrorTooLargeEntity_twoMorePackets() throws CoapException {
        assertEquals(ChangeableResource.INIT_DATA, changeableResource.data);

        String payload = "0_2345678901234|1_2345678901234|2_2345678901234|2";

        //transfer should stop if received code != Code.C231_CONTINUE and report
        CoapResponse msg = client.sendSync(put(CHANGEABLE_RESOURCE_PATH).payload(payload));

        assertEquals(Code.C413_REQUEST_ENTITY_TOO_LARGE, msg.getCode());
        assertEquals(ChangeableResource.INIT_DATA, changeableResource.data);
        // should report maximum allowed data size in Size1 header
        assertEquals(MAX_DATA, msg.options().getSize1().intValue());
    }


    private static class ChangeableResource implements Service<CoapRequest, CoapResponse> {

        private static final Opaque INIT_DATA = of("init data");
        private Opaque data = INIT_DATA;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            switch (req.getMethod()) {
                case GET:
                    return completedFuture(CoapResponse.ok(data));
                case PUT:
                    data = req.getPayload();
                    return completedFuture(CoapResponse.of(Code.C204_CHANGED));
            }
            throw new IllegalStateException();
        }
    }


    @Test
    public void block2_observationWithBlocks() throws Exception {
        System.out.println("\n-- START: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        Opaque expectedBody = OBS_RESOURCE_INIT_VALUE;

        //register observation
        ObservationConsumer obsListener = new ObservationConsumer();
        CoapResponse packet = client.observe(OBSERVABLE_RESOURCE_PATH, obsListener).join();
        assertEquals(expectedBody, packet.getPayload());

        System.out.println("expected: " + expectedBody);
        System.out.println("received: " + packet.getPayload());

        //notif 1
        System.out.println("\n-- NOTIF 1");
        expectedBody = expectedBody.concat(Opaque.of("1_2345678901234|"));
        observableResource.putPayload(expectedBody);
        packet = obsListener.next();
        assertEquals(expectedBody, packet.getPayload());

        System.out.println("expected: " + expectedBody);
        System.out.println("received: " + packet.getPayloadString());

        // actually we will fail only after maxSize + one block
        // first block received by observation code and consequent, with size check,
        // by block mechanism.
        expectedBody = expectedBody.concat(Opaque.of("2_2345678901234|3_2345678901234|"));
        observableResource.putPayload(expectedBody);
        assertTrue(obsListener.isEmpty());


        System.out.println("-- END");

    }


}
