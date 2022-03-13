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

import static com.mbed.coap.server.CoapServerBuilder.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;
import protocolTests.utils.MockCoapTransport;


public class DuplicateTest {

    private CoapServer server;
    private final AtomicInteger duplicated = new AtomicInteger(0);
    private MockCoapTransport.MockClient client;
    private final CompletableFuture<CoapResponse> delayResource = new CompletableFuture<>();
    private Function<CoapResponse, Boolean> consumer = mock(Function.class);

    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .put("/test", req ->
                    completedFuture(CoapResponse.ok("dupa"))
            )
            .get("/test-non", req ->
                    completedFuture(CoapResponse.ok("dupa2"))
            )
            .get("/test-delay", req ->
                    delayResource
            )
            .build();

    @BeforeEach
    public void setUp() throws IOException {
        reset(consumer);
        given(consumer.apply(any())).willReturn(true);

        duplicated.set(0);
        MockCoapTransport serverTransport = new MockCoapTransport();
        AtomicInteger mid = new AtomicInteger(0);

        server = newBuilder()
                .transport(serverTransport)
                .duplicateMsgCacheSize(100)
                .duplicatedCoapMessageCallback(request -> duplicated.incrementAndGet())
                .midSupplier(mid::incrementAndGet)
                .route(route)
                .build();

        server.start();
        client = serverTransport.client();
    }

    @AfterEach
    public void tearDown() {
        assertTrue(client.nothingReceived());
        server.stop();
    }

    @Test
    public void testDuplicateRequest_confirmable() throws Exception {
        // when
        client.send(coap(12).con().put().uriPath("/test"));
        client.send(coap(12).con().put().uriPath("/test"));

        // then
        client.verifyReceived(coap(12).ack(Code.C205_CONTENT).payload("dupa"));
        client.verifyReceived(coap(12).ack(Code.C205_CONTENT).payload("dupa"));

        assertEquals(1, duplicated.get());
    }

    @Test()
    public void testDuplicateRequest_nonConfirmable() throws Exception {
        // when
        client.send(coap(33).non().get().uriPath("/test-non"));
        client.send(coap(33).non().get().uriPath("/test-non"));

        // then
        client.verifyReceived(coap(1).non(Code.C205_CONTENT).payload("dupa2"));
        client.verifyReceived(coap(1).non(Code.C205_CONTENT).payload("dupa2"));

        assertEquals(1, duplicated.get());
    }

    @Test
    public void testDuplicateRequest_withSlowResponse() throws Exception {
        // given
        client.send(coap(11).get().uriPath("/test-delay"));
        client.send(coap(11).get().uriPath("/test-delay"));
        assertTrue(client.nothingReceived());

        // when
        //let response be sent
        delayResource.complete(CoapResponse.ok("dupa3"));

        // then
        client.verifyReceived(coap(11).ack(Code.C205_CONTENT).payload("dupa3"));
        assertEquals(1, duplicated.get());
    }

    @Test
    void observationRepeated() throws Exception {
        CoapClient outbound = CoapClientBuilder.clientFor(LOCAL_5683, server);

        // given, observation established
        outbound.observe("/obs", Opaque.variableUInt(1), consumer);
        client.verifyReceived(coap(1).token(1).get().uriPath("/obs").obs(0));
        client.send(coap(1).token(1).ack(Code.C205_CONTENT).payload("01").obs(1));

        // when
        client.send(coap(133).token(1).con(Code.C205_CONTENT).payload("02").obs(2));
        client.send(coap(133).token(1).con(Code.C205_CONTENT).payload("02").obs(2));
        client.send(coap(133).token(1).con(Code.C205_CONTENT).payload("02").obs(2));

        // then
        client.verifyReceived(coap(133).ack(null));
        client.verifyReceived(coap(133).ack(null));
        client.verifyReceived(coap(133).ack(null));
        assertEquals(2, duplicated.get());
        verify(consumer, times(1)).apply(any());
    }

    @Test
    void observationResponseRepeated() throws Exception {
        CoapClient outbound = CoapClientBuilder.clientFor(LOCAL_5683, server);

        // given
        outbound.observe("/obs", Opaque.variableUInt(1), consumer);
        client.verifyReceived(coap(1).token(1).get().uriPath("/obs").obs(0));

        // when
        client.send(coap(1).token(1).ack(Code.C205_CONTENT).payload("01").obs(1));
        client.send(coap(1).token(1).ack(Code.C205_CONTENT).payload("01").obs(1));

        // then
        verify(consumer, times(0)).apply(any());

        // and
        client.send(coap(133).token(1).con(Code.C205_CONTENT).payload("02").obs(2));
        client.verifyReceived(coap(133).ack(null));
        verify(consumer, times(1)).apply(any());
    }

    private static CoapPacketBuilder coap(int mid) {
        return newCoapPacket(LOCAL_5683).mid(mid);
    }

}
