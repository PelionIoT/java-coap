/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.transport.udp.DatagramSocketTransport.udp;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.server.filter.TokenGeneratorFilter;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UsageTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageTest.class);
    private CoapServer server;

    @BeforeAll
    void setUp() throws IOException {
        serverUsage();
    }

    @AfterAll
    void tearDown() {
        server.stop();
    }

    void serverUsage() throws IOException {
        server = CoapServer.builder()
                // configure with plain text UDP transport, listening on port 5683
                .transport(new DatagramSocketTransport(5683))
                // define routing
                // (note that each resource function is a `Service` type and can be decorated/transformed with `Filter`)
                .route(RouterService.builder()
                        .get("/.well-known/core", req -> completedFuture(
                                CoapResponse.ok("</sensors/temperature>", MediaTypes.CT_APPLICATION_LINK__FORMAT)
                        ))
                        .post("/actuators/switch", req -> {
                            // ...
                            return completedFuture(CoapResponse.of(Code.C204_CHANGED));
                        })
                        // observable resource
                        .get("/sensors/temperature", req -> {
                            CoapResponse resp = CoapResponse.ok("21C").nextSupplier(() -> {
                                        // we need to define a promise for next value
                                        CompletableFuture<CoapResponse> promise = new CompletableFuture();
                                        // ... complete once resource value changes
                                        return promise;
                                    }
                            );

                            return completedFuture(resp);
                        })
                )
                .build();

        server.start();
    }

    @Test
    void simpleClientUsage() throws IOException, CoapException {
        // build CoapClient that connects to coap server which is running on port 5683
        CoapClient client = CoapServer.builder()
                .transport(udp())
                .buildClient(new InetSocketAddress("localhost", 5683));

        // send request
        CoapResponse resp = client.sendSync(CoapRequest.get("/sensors/temperature"));
        LOGGER.info(resp.toString());

        client.close();
    }

    @Test
    void completeClientUsage() throws IOException, CoapException {
        // build CoapClient that connects to coap server which is running on port 5683
        CoapClient client = CoapServer.builder()
                // define transport, plain text UDP listening on random port
                .transport(udp())
                // (optional) define maximum block size
                .blockSize(BlockSize.S_1024)
                // (optional) set maximum response timeout, default for every request
                .responseTimeout(Duration.ofMinutes(2))
                // (optional) set maximum allowed resource size
                .maxIncomingBlockTransferSize(1000_0000)
                // (optional) set extra filters (interceptors) to outbound pipeline
                .outboundFilter(
                        // each request will be set with different Token
                        TokenGeneratorFilter.sequential(1)
                )
                // build client with target server address
                .buildClient(new InetSocketAddress("localhost", 5683));

        // send ping
        client.ping();

        // send request
        CompletableFuture<CoapResponse> futureResponse = client.send(CoapRequest.get("/sensors/temperature"));
        futureResponse.thenAccept(resp ->
                // .. handle response
                LOGGER.info(resp.toString())
        );

        // send request with payload and header options
        CompletableFuture<CoapResponse> futureResponse2 = client.send(CoapRequest
                .post("/actuator/switch")
                .options(opt -> {
                    // set header options, for example:
                    opt.setEtag(Opaque.decodeHex("0a8120"));
                    opt.setAccept(MediaTypes.CT_APPLICATION_JSON);
                    opt.setMaxAge(3600L);
                })
                .payload("{\"power\": \"on\"}", MediaTypes.CT_APPLICATION_JSON)
                .context(TransportContext.RESPONSE_TIMEOUT, Duration.ofMinutes(3)) // overwrite default response timeout
        );
        futureResponse2.thenAccept(resp ->
                // .. handle response
                LOGGER.info(resp.toString())
        );

        // observe resource (subscribe), observations will be handled to provided callback
        CompletableFuture<CoapResponse> resp3 = client.observe("/sensors/temperature", coapResponse -> {
            LOGGER.info(coapResponse.toString());
            return true; // return false to terminate observation
        });
        LOGGER.info(resp3.join().toString());

        client.close();
    }

}
