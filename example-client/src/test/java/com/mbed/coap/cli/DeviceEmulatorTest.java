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
package com.mbed.coap.cli;

import static com.mbed.coap.packet.CoapResponse.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.cli.providers.PlainTextProvider;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.IOException;
import java.text.ParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by szymon
 */
public class DeviceEmulatorTest {

    CoapServer stubServer;

    @BeforeEach
    public void setUp() throws Exception {

        DatagramSocketTransport transport = new DatagramSocketTransport(0);
        stubServer = CoapServerBuilder
                .newBuilder()
                .route(RouterService.builder()
                        .post("/rd", req -> {
                            String epName;
                            try {
                                epName = req.options().getUriQueryMap().get("ep");
                            } catch (ParseException e) {
                                return completedFuture(badRequest());
                            }
                            return completedFuture(new CoapResponse(Code.C201_CREATED, Opaque.EMPTY, o -> o.setLocationPath("/rd/" + epName)));
                        })
                )
                .transport(transport)
                .build();

        stubServer.start();
    }

    @AfterEach
    public void tearDown() {
        stubServer.stop();
    }

    @Test
    public void registerEmulator() throws IOException {
        final int port = stubServer.getLocalSocketAddress().getPort();

        DeviceEmulator deviceEmulator = new DeviceEmulator(new CoapSchemes());
        deviceEmulator.start(new PlainTextProvider(), String.format("coap://localhost:%d/rd?ep=dev123&lt=60", port), null);


        await().untilAsserted(() -> {
            assertTrue(deviceEmulator.getRegistrationManager().isRegistered());
        });
    }
}
