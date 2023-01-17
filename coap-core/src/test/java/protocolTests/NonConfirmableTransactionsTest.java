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

import static com.mbed.coap.packet.BlockSize.S_16;
import static com.mbed.coap.packet.CoapRequest.put;
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.server.CoapServerBuilder.newBuilder;
import static com.mbed.coap.transport.TransportContext.NON_CONFIRMABLE;
import static com.mbed.coap.utils.Validations.require;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.reset;
import static protocolTests.utils.CoapPacketBuilder.LOCAL_5683;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;
import protocolTests.utils.MockCoapTransport;

public class NonConfirmableTransactionsTest {

    private CoapServer server;
    private MockCoapTransport.MockClient client;
    private Function<CoapResponse, Boolean> consumer = mock(Function.class);

    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .get("/test", req -> {
                        require(req.getTransContext().get(NON_CONFIRMABLE));
                        return completedFuture(ok("OK"));
                    }
            )
            .get("/large", req -> {
                        require(req.getTransContext().get(NON_CONFIRMABLE));
                        return completedFuture(ok("aaaaaaaaaaaaaaa|bbbbbb"));
                    }
            )
            .build();

    @BeforeEach
    public void setUp() throws IOException {
        reset(consumer);
        given(consumer.apply(any())).willReturn(true);

        MockCoapTransport serverTransport = new MockCoapTransport();
        AtomicInteger mid = new AtomicInteger(1000);

        server = newBuilder()
                .transport(serverTransport)
                .midSupplier(mid::incrementAndGet)
                .route(route)
                .build();

        server.start();
        client = serverTransport.client();
    }

    @Test()
    public void inboundSimpleRequest() throws Exception {
        // when
        client.send(coap(123).non().token(30).get().uriPath("/test"));

        // then
        client.verifyReceived(coap(1001).non(Code.C205_CONTENT).token(30).payload("OK"));
    }

    @Test
    void outboundSimpleRequest() throws InterruptedException {
        // given
        CompletableFuture<CoapResponse> resp = server.clientService().apply(put(LOCAL_5683, "/test2").token(120).context(NON_CONFIRMABLE, true));
        client.verifyReceived(coap(1001).non().put().token(120).uriPath("/test2"));

        // when
        client.send(coap(4310).non(Code.C201_CREATED).token(120));

        // then
        assertEquals(CoapResponse.of(Code.C201_CREATED), resp.join());
    }

    @Test
    void inboundRequestWithBlocks() throws InterruptedException {
        client.send(coap(123).non().get().token(31).uriPath("/large").block2Res(0, S_16, false));
        client.verifyReceived(coap(1001).non(Code.C205_CONTENT).token(31).block2Res(0, S_16, true).payload("aaaaaaaaaaaaaaa|"));

        client.send(coap(124).non().token(32).get().uriPath("/large").block2Res(1, S_16, false));
        client.verifyReceived(coap(1002).token(32).non(Code.C205_CONTENT).block2Res(1, S_16, false).payload("bbbbbb"));
    }

    @Test
    void outboundRequestWithBlocks() throws InterruptedException {
        // given
        CompletableFuture<CoapResponse> resp = server.clientService().apply(put(LOCAL_5683, "/large2").token(32).context(NON_CONFIRMABLE, true));
        client.verifyReceived(coap(1001).non().put().token(32).uriPath("/large2"));

        // when
        client.send(coap(4310).non(Code.C201_CREATED).token(32).block2Res(0, S_16, true).payload("cccccccccccccccc"));
        client.verifyReceived(coap(1002).non().put().token(32).block2Res(1, S_16, false).uriPath("/large2"));
        client.send(coap(4310).non(Code.C201_CREATED).token(32).block2Res(1, S_16, false).payload("dddd"));

        // then
        assertEquals(CoapResponse.of(Code.C201_CREATED).block2Res(1, S_16, false).payload(Opaque.of("ccccccccccccccccdddd")), resp.join());
    }

    private static CoapPacketBuilder coap(int mid) {
        return newCoapPacket(LOCAL_5683).mid(mid);
    }
}
