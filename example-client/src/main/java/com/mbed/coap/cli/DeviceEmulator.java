/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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

import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.client.RegistrationManager;
import com.mbed.coap.observe.ObservableResourceService;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public class DeviceEmulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceEmulator.class);
    protected CoapServer emulatorServer;
    protected final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private RegistrationManager registrationManager;
    protected final CoapSchemes providers;

    public static void main(String[] args) {
        main(args, new DeviceEmulator(new CoapSchemes()));
    }

    public static void main(String[] args, DeviceEmulator deviceEmulator) {
        if (args.length == 0) {
            System.out.println("Usage: ");
            System.out.println("  ./run.sh [options...] <scheme>://<registration-url>");
            System.out.println("Options:");
            System.out.println("     -s <ssl provider>  jdk <default>,");
            System.out.println("                        openssl (requires installed openssl that supports dtls),");
            System.out.println("                        stdio (standard IO)");
            System.out.println("     -k <file>          KeyStore file");
            System.out.println("Schemes: " + deviceEmulator.providers.supportedSchemes().replaceAll("\n", "\n         "));
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  ./run.sh 'coap://localhost:5683/rd?ep=device01&aid=dm'");
            System.out.println("  ./run.sh -k device01.jks 'coaps+tcp://localhost:5685/rd?ep=device01&aid=dm'");
            return;
        }

        //parse arguments
        String keystoreFile = null;
        TransportProvider transportProvider = deviceEmulator.providers.defaultProvider();
        String cipherSuite = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-k")) {
                keystoreFile = args[++i];
            } else if (args[i].equals("-s")) {
                transportProvider = deviceEmulator.providers.transportProviderFor(args[++i]);
            } else if (args[i].equals("--cipher")) {
                cipherSuite = args[++i];
            }
        }

        transportProvider.setCipherSuite(cipherSuite);

        String uri = args[args.length - 1];

        try {
            deviceEmulator.start(transportProvider, uri, keystoreFile);
            Runtime.getRuntime().addShutdownHook(new Thread(deviceEmulator::stop));
        } catch (IllegalArgumentException ex) {
            LOGGER.error(ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public DeviceEmulator(CoapSchemes providers) {
        this.providers = providers;
    }


    void start(TransportProvider transportProvider, String registrationUri, String keystoreFile) throws IOException {
        URI uri = URI.create(registrationUri);

        emulatorServer = providers.create(transportProvider, keystoreFile, uri)
                .route(createRouting())
                .build().start();

        //registration
        String links = "</3/0/1>,</3/0/2>,</3/0/3>,</delayed-10s>";
        this.registrationManager = new RegistrationManager(emulatorServer, uri, links, scheduledExecutor);
        LOGGER.info("Resources: {}", links);
        registrationManager.register();
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


    RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    void stop() {
        registrationManager.removeRegistration();
        emulatorServer.stop();
    }
}
