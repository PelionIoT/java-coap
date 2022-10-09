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
package com.mbed.coap.server;


import static com.mbed.coap.packet.CoapResponse.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.utils.AsyncQueue;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoapServerTest {

    private Consumer<CoapPacket> dispatcher = mock(Consumer.class);
    private CoapTransport transport = mock(CoapTransport.class);
    private Runnable stop = mock(Runnable.class);
    private CoapServer server;
    private final AsyncQueue<CoapPacket> receiveQueue = new AsyncQueue<>();

    @BeforeEach
    public void setUp() throws Exception {
        reset(dispatcher, transport, stop);
        given(transport.receive()).willAnswer(__ -> receiveQueue.poll());
        receiveQueue.removeAll();

        server = new CoapServer(transport, dispatcher, __ -> completedFuture(ok("OK")), stop).start();
    }

    @Test
    public void shouldStartAndStop() throws Exception {
        verify(transport).start();
        assertTrue(server.isRunning());

        server.stop();
        verify(transport).stop();
        verify(stop).run();
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldDoNothingWhenAttemptToStopWhenNotRunning() throws Exception {
        final CoapServer nonStartedServer = new CoapServer(transport, dispatcher, __ -> completedFuture(ok("OK")), stop);

        nonStartedServer.stop();
    }

    @Test
    public void shouldFailWhenAttemptToStartWhenALreadyRunning() throws Exception {
        assertThatThrownBy(server::start)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldReadPacketAndPassItToDispatcher() {
        CoapPacket coapPacket1 = newCoapPacket(1).get().uriPath("/test").build();
        CoapPacket coapPacket2 = newCoapPacket(2).get().uriPath("/test").build();
        CoapPacket coapPacket3 = newCoapPacket(3).get().uriPath("/test").build();

        receiveQueue.add(coapPacket1);
        verify(dispatcher).accept(eq(coapPacket1));

        receiveQueue.add(coapPacket2);
        verify(dispatcher).accept(eq(coapPacket2));

        receiveQueue.add(coapPacket3);
        verify(dispatcher).accept(eq(coapPacket3));
    }

    @Test
    public void shouldStopWhenReadingFails() {
        assertTrue(server.isRunning());

        // when
        receiveQueue.addException(new IOException());
        receiveQueue.add(newCoapPacket(1).get().uriPath("/test").build());

        // then
        verify(transport).stop();
        verify(stop).run();
        assertFalse(server.isRunning());

    }
}
