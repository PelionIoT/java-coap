/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.observe;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.packet.Opaque.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.IpPortAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ObservableResourceServiceTest {

    private ObservableResourceService obsRes = null;
    private static final InetSocketAddress PEER_1 = IpPortAddress.local(15683).toInetSocketAddress();
    private static final InetSocketAddress PEER_2 = IpPortAddress.local(25683).toInetSocketAddress();

    @BeforeEach
    public void setUp() throws Exception {
        obsRes = new ObservableResourceService(CoapResponse.ok("test"));
    }

    @Test
    public void createObservationRelation() throws ExecutionException, InterruptedException {
        CompletableFuture<CoapResponse> resp = obsRes.apply(get(PEER_1, "/test").token(13).observe(0));

        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test"), opts -> opts.setObserve(0)), resp.get());
        assertNotNull(resp.get().next);
    }

    @Test
    public void doNotCreateObservationRelationWhenMissingObs() throws ExecutionException, InterruptedException {
        // when
        CompletableFuture<CoapResponse> resp = obsRes.apply(get(PEER_1, "/test"));

        // then
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test")), resp.get());
        assertNull(resp.get().next);
    }

    @Test
    public void doNotCreateObservationRelationWhenObsIsNot0() throws ExecutionException, InterruptedException {
        // when
        CompletableFuture<CoapResponse> resp = obsRes.apply(get(PEER_1, "/test").token(13).observe(10));

        // then
        assertEquals(CoapResponse.ok("test"), resp.get());
        assertNull(resp.get().next);
    }

    @Test
    public void sendObservation() throws ExecutionException, InterruptedException {
        // given
        Supplier<CompletableFuture<CoapResponse>> next = subscribe(PEER_1);
        CompletableFuture<CoapResponse> obs = next.get();

        // when
        obsRes.putPayload(of("test2"));

        // then
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(1)), obs.get());
    }

    @Test
    public void sendMultipleObservationsWithExtraHeaders() throws ExecutionException, InterruptedException {
        // given
        Supplier<CompletableFuture<CoapResponse>> next = subscribe(PEER_1);
        CompletableFuture<CoapResponse> obs = next.get();

        // when
        obsRes.put(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setEtag(of("200"))));
        CompletableFuture<CoapResponse> obs2 = next.get();
        obsRes.put(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> opts.setEtag(of("300"))));

        // then
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> {
            opts.setObserve(1);
            opts.setEtag(of("200"));
        }), obs.get());

        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test3"), opts -> {
            opts.setObserve(2);
            opts.setEtag(of("300"));
        }), obs2.get());
    }

    @Test
    public void sendObservationToMultipleSubscribers() throws ExecutionException, InterruptedException {
        // given
        CompletableFuture<CoapResponse> obs1 = subscribe(PEER_1).get();
        CompletableFuture<CoapResponse> obs2 = subscribe(PEER_2).get();

        // when
        obsRes.putPayload(of("test2"));

        // then
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(1)), obs1.get());
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(1)), obs2.get());
    }

    @Test
    public void sendCancelObservation() throws ExecutionException, InterruptedException {
        // given
        Supplier<CompletableFuture<CoapResponse>> next = subscribe(PEER_1);
        CompletableFuture<CoapResponse> obs = next.get();

        // when
        obsRes.put(CoapResponse.notFound());

        // then
        assertEquals(new CoapResponse(Code.C404_NOT_FOUND, EMPTY, opts -> opts.setObserve(1)), obs.get());
        assertEquals(CoapResponse.notFound(), obsRes.apply(get(PEER_1, "/")).get());
    }

    @Test
    public void deregisterObservationRelation() throws ExecutionException, InterruptedException {
        // given
        CompletableFuture<CoapResponse> obs1 = subscribe(PEER_1).get();
        CompletableFuture<CoapResponse> obs2 = subscribe(PEER_2).get();

        // when
        CompletableFuture<CoapResponse> resp2 = obsRes.apply(get(PEER_1, "/test").token(13).observe(1));
        // and
        obsRes.putPayload(of("test2"));

        // then
        assertEquals(CoapResponse.ok("test"), resp2.get());
        assertNull(resp2.get().next);

        assertTrue(obs1.isCancelled());
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(1)), obs2.get());
    }

    @Test
    public void reRegister() throws ExecutionException, InterruptedException {
        // given
        CompletableFuture<CoapResponse> obs1 = subscribe(PEER_1).get();

        // when
        CompletableFuture<CoapResponse> obs2 = subscribe(PEER_1).get();
        // and
        obsRes.putPayload(of("test2"));

        // then
        assertTrue(obs1.isCancelled());
        assertEquals(new CoapResponse(Code.C205_CONTENT, of("test2"), opts -> opts.setObserve(1)), obs2.get());
    }

    private Supplier<CompletableFuture<CoapResponse>> subscribe(InetSocketAddress peerAddress) throws InterruptedException, ExecutionException {
        CompletableFuture<CoapResponse> resp = obsRes.apply(get(peerAddress, "/test").token(13).observe(0));
        return resp.get().next;
    }

}