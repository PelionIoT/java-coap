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


import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.eq;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.filter.MaxAllowedPayloadFilter;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoapServerTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CompletableFuture<CoapResponse> sendPromise;
    private CoapServer server;
    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .post("/some*", req ->
                    completedFuture(CoapResponse.ok("OK"))
            )
            .post("/err", req ->
                    failedFuture(new CoapException("error-007"))
            )
            .post("/err2", new MaxAllowedPayloadFilter(100, "too big").then(req ->
                    failedFuture(new RuntimeException()))
            )
            .build();


    @BeforeEach
    public void setUp() throws Exception {
        reset(msg);
        given(msg.send(any(SeparateResponse.class))).willAnswer(__ -> completedFuture(true));
        given(msg.send(any(CoapRequest.class))).willAnswer(__ -> {
            sendPromise = new CompletableFuture<>();
            return sendPromise;
        });

        server = new CoapServer(msg, Filter.identity(), route, Filter.identity()).start();
    }

    @Test
    public void shouldStartAndStop() throws Exception {
        verify(msg).start(any(), any());
        assertTrue(server.isRunning());

        server.stop();
        verify(msg).stop();
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldFailWhenAttemptToStopWhenNotRunning() throws Exception {
        final CoapServer nonStartedServer = new CoapServer(msg, Filter.identity(), RouterService.NOT_FOUND_SERVICE, Filter.identity());

        assertThatThrownBy(nonStartedServer::stop).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldPassMakeRequest_toMessaging() throws ExecutionException, InterruptedException {
        //when
        final CompletableFuture<CoapResponse> resp = server.clientService().apply(get("/test"));

        //then
        verify(msg).send(eq(get("/test")));
        assertFalse(resp.isDone());

        //verify callback
        sendPromise.complete(badRequest());
        assertTrue(resp.isDone());
        assertEquals(Code.C400_BAD_REQUEST, resp.get().getCode());
    }

    @Test
    public void should_pass_disconnectionHandler() {
        Consumer<InetSocketAddress> disconnectConsumer = inetSocketAddress -> {
        };

        //when
        server.setConnectHandler(disconnectConsumer);

        //then
        verify(msg).setConnectHandler(eq(disconnectConsumer));
    }

}
