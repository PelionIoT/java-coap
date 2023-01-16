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

import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.packet.Opaque.decodeHex;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.server.filter.EtagGeneratorFilter;
import com.mbed.coap.transport.InMemoryCoapTransport;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientTest {

    private CoapServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = CoapServer.builder()
                .transport(InMemoryCoapTransport.create(5683))
                .route(RouterService.builder()
                        .get("/test", req -> completedFuture(ok("OK!"))))
                .build()
                .start();

    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void clientWithExtraOutboundFilters() throws IOException, CoapException {

        CoapClient client = CoapServer.builder()
                .transport(InMemoryCoapTransport.create())
                .outboundFilter(EtagGeneratorFilter.PAYLOAD_HASHING)
                .buildClient(InMemoryCoapTransport.createAddress(5683));

        CoapResponse coapResponse = client.sendSync(CoapRequest.get("/test"));

        assertEquals(CoapResponse.ok("OK!").etag(decodeHex("01a624")), coapResponse);

        client.close();
    }
}
