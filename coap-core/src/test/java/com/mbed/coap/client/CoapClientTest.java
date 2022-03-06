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
package com.mbed.coap.client;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.MediaTypes.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.FutureQueue;
import com.mbed.coap.utils.ObservationConsumer;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoapClientTest {
    private CoapClient client;
    private final Service<CoapRequest, CoapResponse> clientService = mock(Service.class);
    private FutureQueue<CoapResponse> next = null;
    private final Opaque token1001 = Opaque.ofBytes(0x03, 0xE9);


    @BeforeEach
    public void setUp() throws Exception {
        reset(clientService);
        client = new CoapClient(LOCAL_5683, clientService, () -> {
        });
        next = new FutureQueue<>();
    }

    @Test
    public void request() {
        given(clientService.apply(get(LOCAL_5683, "/test")))
                .willReturn(completedFuture(CoapResponse.ok("ABC", CT_TEXT_PLAIN)));

        // when
        CompletableFuture<CoapResponse> resp = client.send(get(LOCAL_5683, "/test"));

        // then
        assertEquals("ABC", resp.join().getPayloadString());
    }

    @Test
    public void pingRequest() throws Exception {
        given(clientService.apply(ping(LOCAL_5683, TransportContext.EMPTY)))
                .willReturn(completedFuture(CoapResponse.of(null)));

        // when
        CompletableFuture<CoapResponse> resp = client.ping();

        // then
        assertNotNull(resp.get());
    }

    @Test
    public void syncRequest() throws CoapException {
        given(clientService.apply(get(LOCAL_5683, "/test")))
                .willReturn(completedFuture(CoapResponse.ok("ABC", CT_TEXT_PLAIN)));

        // when
        CoapResponse resp = client.sendSync(get(LOCAL_5683, "/test"));

        // then
        assertEquals("ABC", resp.getPayloadString());
    }


    @Test
    public void observationTest() throws Exception {
        given(clientService.apply(get(LOCAL_5683, "/test").token(token1001).observe(0)))
                .willReturn(completedFuture(CoapResponse.ok("1", CT_TEXT_PLAIN).options(o -> o.setObserve(1)).nextSupplier(next)));

        ObservationConsumer obsConsumer = new ObservationConsumer();
        // when
        CompletableFuture<CoapResponse> resp = client.observe("/test", token1001, obsConsumer);

        // then
        assertEquals("1", resp.get().getPayloadString());

        // and then observation
        next.put(CoapResponse.ok("2", CT_TEXT_PLAIN).options(o -> o.setObserve(2)));
        assertEquals(CoapResponse.ok("2", CT_TEXT_PLAIN).options(o -> o.setObserve(2)), obsConsumer.next());

    }

    @Test
    public void shouldTerminateObservation() {
        given(clientService.apply(get(LOCAL_5683, "/test").token(token1001).observe(0)))
                .willReturn(completedFuture(CoapResponse.ok("1", CT_TEXT_PLAIN).options(o -> o.setObserve(1)).nextSupplier(next)));
        ObservationConsumer obsConsumer = new ObservationConsumer();

        // given, established observation relation
        CompletableFuture<CoapResponse> resp = client.observe("/test", token1001, obsConsumer);
        assertEquals("1", resp.join().getPayloadString());

        // when
        next.put(CoapResponse.notFound());

        // then
        assertEquals(CoapResponse.notFound(), obsConsumer.next());
        assertNull(next.promise);
    }

}