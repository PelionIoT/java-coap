/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.client.RegistrationManager;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.ObservableResourceService;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.utils.Service;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "register", mixinStandardHelpOptions = true, description = "Register to LwM2M server and simulate some simple resources", usageHelpAutoWidth = true)
public class DeviceEmulator implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceEmulator.class);

    @Parameters(index = "0", paramLabel = "<registration-url>", description = "Registration url")
    private URI uri;

    @Mixin
    private TransportOptions transportOptions;

    protected CoapServer emulatorServer;
    protected final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    RegistrationManager registrationManager;

    @Override
    public Integer call() throws Exception {
        emulatorServer = transportOptions.create(uri,
                udpBuilder -> udpBuilder.route(createRouting()).build(),
                tcpBuilder -> tcpBuilder.route(createRouting()).build()
        );
        emulatorServer.start();

        //registration
        String links = "</3/0/1>,</3/0/2>,</3/0/3>,</delayed-10s>";
        this.registrationManager = new RegistrationManager(emulatorServer, uri, links, scheduledExecutor);
        LOGGER.info("Resources: {}", links);
        registrationManager.register().join();

        if (registrationManager.isRegistered()) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            return -1;
        } else {
            return 1;
        }
    }

    protected Service<CoapRequest, CoapResponse> createRouting() {
        ObservableResourceService timeResource = new ObservableResourceService(CoapResponse.ok(Instant.now().toString()));
        scheduledExecutor.scheduleAtFixedRate(() ->
                        timeResource.putPayload(Opaque.of(Instant.now().toString())),
                30, 30, TimeUnit.SECONDS
        );

        return RouterService.builder()
                .get("/3/0/1", __ -> completedFuture(CoapResponse.ok("Acme")))
                .get("/3/0/2", __ -> completedFuture(CoapResponse.ok("Emulator")))
                .get("/3/0/3", __ -> completedFuture(CoapResponse.ok("0.0.1")))
                .get("/delayed-10s", __ -> {
                    CompletableFuture<CoapResponse> promise = new CompletableFuture<>();
                    scheduledExecutor.schedule(() -> promise.complete(CoapResponse.ok("OK")), 10, TimeUnit.SECONDS);
                    return promise;
                })
                .get("/time", timeResource)
                .build();

    }

    void stop() {
        registrationManager.removeRegistration();
        emulatorServer.stop();
    }
}
