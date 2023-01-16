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
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.transport.InMemoryCoapTransport.createAddress;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class UnreliableTransportTest {

    private CoapServer server = null;
    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .get("/test/1", __ -> completedFuture(CoapResponse.ok("Dziala")))
            .get("/dropping", __ -> completedFuture(ok("OK")))
            .build();

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServer.builder()
                .transport(InMemoryCoapTransport.create(5683))
                .route(route)
                .build();
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }


    @Test
    public void requestWithPacketLost() throws CoapException, IOException {
        try (CoapClient cnn = CoapServer.builder()
                .transport(new DroppingPacketsTransportWrapper(0, (byte) 0) {
                    private boolean hasDropped = false;

                    @Override
                    public void sendPacket0(CoapPacket coapPacket) {
                        //will drop only first packet
                        if (!hasDropped) {
                            hasDropped = true;
                            System.out.println("dropped");
                        } else {
                            super.sendPacket0(coapPacket);
                        }
                    }

                })
                .retransmission(new CoapTimeout(100))
                .buildClient(InMemoryCoapTransport.createAddress(5683))
        ) {
            CoapResponse resp = cnn.sendSync(get("/dropping"));
            assertEquals("OK", resp.getPayloadString());
        }
    }

    @Test
    public void testRequestWithPacketDelay() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        CoapClient client = CoapServer.builder()
                .transport(new InMemoryCoapTransport(0, command -> executorService.execute(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException();
                    }
                    command.run();
                }))).buildClient(createAddress(5683));

        CompletableFuture<CoapResponse> callback = client.send(get("/test/1"));
        assertEquals("Dziala", callback.get().getPayloadString());
        client.close();
    }

    @Test
    public void testRequestWithPacketDropping() throws IOException, CoapException {
        server.stop();
        server = CoapServer.builder()
                .transport(new DroppingPacketsTransportWrapper(CoapConstants.DEFAULT_PORT, (byte) 100))
                .route(RouterService.builder()
                        .get("/test", __ -> completedFuture(ok("TEST")))
                        .build())
                .build()
                .start();

        CoapClient cnn = CoapServer.builder()
                .transport(InMemoryCoapTransport.create()).retransmission(new SingleTimeout(100))
                .buildClient(InMemoryCoapTransport.createAddress(CoapConstants.DEFAULT_PORT));

        assertThrows(CoapTimeoutException.class, () ->
                cnn.sendSync(get("/test"))
        );
    }

    private static class DroppingPacketsTransportWrapper extends InMemoryCoapTransport {
        private final byte probability; //0-100
        private final Random r = new Random();

        private boolean drop() {
            return probability > 0 && r.nextInt(100) < probability;
        }

        private DroppingPacketsTransportWrapper(int port, int probability) {
            super(port);
            if (probability < 0 || probability > 100) {
                throw new IllegalArgumentException("Value must be in range 0-100");
            }
            this.probability = ((byte) probability);
        }


        @Override
        public void receive(InMemoryCoapTransport.DatagramMessage msg) {
            if (!drop()) {
                super.receive(msg);
            }
        }
    }

}
