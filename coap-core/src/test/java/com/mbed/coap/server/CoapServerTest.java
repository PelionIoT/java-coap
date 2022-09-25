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
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.*;
import com.mbed.coap.server.messaging.CoapMessaging;
import com.mbed.coap.transport.CoapTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoapServerTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CoapTransport transport = mock(CoapTransport.class);
    private CoapServer server;

    @BeforeEach
    public void setUp() throws Exception {
        reset(msg);
        server = new CoapServer(transport, msg, __ -> completedFuture(ok("OK"))).start();
    }

    @Test
    public void shouldStartAndStop() throws Exception {
        verify(msg).start();
        verify(transport).start(eq(msg));
        assertTrue(server.isRunning());

        server.stop();
        verify(msg).stop();
        verify(transport).stop();
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldFailWhenAttemptToStopWhenNotRunning() throws Exception {
        final CoapServer nonStartedServer = new CoapServer(transport, msg, __ -> completedFuture(ok("OK")));

        assertThatThrownBy(nonStartedServer::stop).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldFailWhenAttemptToStartWhenALreadyRunning() throws Exception {
        assertThatThrownBy(server::start)
                .isInstanceOf(IllegalStateException.class);
    }

}
