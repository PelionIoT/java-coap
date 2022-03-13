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
package com.mbed.coap.server.messaging;

import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.DefaultDuplicateDetectorCache;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

    private DefaultDuplicateDetectorCache cache = new DefaultDuplicateDetectorCache("", 100, 1000, 1000, 1000, Executors.newSingleThreadScheduledExecutor());
    private DuplicateDetector duplicateDetector = new DuplicateDetector(cache, DuplicatedCoapMessageCallback.NULL);
    private Service<CoapPacket, CoapPacket> service = mock(Service.class);

    @BeforeEach
    void setUp() {
        reset(service);
    }

    @Test
    void shouldRecognizeRetransmissionAndUseCachedResponse() {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(1).get().uriPath("/test").build();
        CoapPacket resp = newCoapPacket(LOCAL_5683).mid(1).ack(Code.C205_CONTENT).build();
        given(service.apply(any())).willReturn(completedFuture(resp));

        CompletableFuture<CoapPacket> result1 = duplicateDetector.apply(req, service);
        CompletableFuture<CoapPacket> result2 = duplicateDetector.apply(req, service);
        CompletableFuture<CoapPacket> result3 = duplicateDetector.apply(req, service);

        assertEquals(resp, result1.join());
        assertEquals(resp, result2.join());
        assertEquals(resp, result3.join());
        verify(service, times(1)).apply(any());
    }

    @Test
    void shouldIgnoreRetransmissionWhenNoResponseIsReady() {
        CoapPacket req = newCoapPacket(LOCAL_5683).mid(1).get().uriPath("/test").build();
        given(service.apply(any())).willReturn(new CompletableFuture<>());

        CompletableFuture<CoapPacket> result1 = duplicateDetector.apply(req, service);
        CompletableFuture<CoapPacket> result2 = duplicateDetector.apply(req, service);
        CompletableFuture<CoapPacket> result3 = duplicateDetector.apply(req, service);

        assertFalse(result1.isDone());
        assertNull(result2.join());
        assertNull(result3.join());
        verify(service, times(1)).apply(any());
    }
}