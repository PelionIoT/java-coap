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

import com.mbed.coap.client.RegistrationManager;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.observe.SimpleObservableResource;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
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
    private final CoapServer emulatorServer;
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private final RegistrationManager registrationManager;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: ");
            System.out.println("  ./run.sh [-k KEYSTORE_FILE] <schema>://<registration-url> \n");
            System.out.println("Schemas: " + CoapSchemas.INSTANCE.supportedSchemas().replaceAll("\n", "\n         "));
            System.out.println("\nExamples:");
            System.out.println("  ./run.sh -k device01.jks 'coaps+tcp://localhost:5685/rd?ep=device01&aid=d'");
            System.out.println("  ./run.sh -k device01.jks 'coaps+tcp-d2://localhost:5684/rd?ep=device01&aid=d'");
            return;
        }

        //parse arguments
        String keystoreFile = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-k")) {
                keystoreFile = args[++i];
            }
        }
        String uri = args[args.length - 1];

        final DeviceEmulator deviceEmulator = new DeviceEmulator(uri, keystoreFile);

        Runtime.getRuntime().addShutdownHook(new Thread(deviceEmulator::stop));
    }


    public DeviceEmulator(String registrationUri, String keystoreFile) throws IOException {
        URI uri = URI.create(registrationUri);

        emulatorServer = CoapSchemas.INSTANCE.create(keystoreFile, uri).build().start();


        //read only resources
        emulatorServer.addRequestHandler("/3/0/1", new ReadOnlyCoapResource("ARM"));
        emulatorServer.addRequestHandler("/3/0/2", new ReadOnlyCoapResource("Emulator"));
        emulatorServer.addRequestHandler("/3/0/3", new ReadOnlyCoapResource("0.0.1"));
        emulatorServer.addRequestHandler("/delayed-10s", new DelayedReadOnlyCoapResource("OK", 10));

        //observable resource
        SimpleObservableResource timeResource = new SimpleObservableResource("", emulatorServer);
        emulatorServer.addRequestHandler("/time", timeResource);
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                timeResource.setBody(new Date().toString());
            } catch (CoapException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }, 30, 30, TimeUnit.SECONDS);


        //registration
        this.registrationManager = new RegistrationManager(emulatorServer, uri, scheduledExecutor);
        registrationManager.register();
    }



    RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    void stop() {
        registrationManager.removeRegistration();
        emulatorServer.stop();
    }




    private class DelayedReadOnlyCoapResource extends ReadOnlyCoapResource {

        private final int delay;

        public DelayedReadOnlyCoapResource(String resp, int delay) {
            super(resp, null, 0);
            this.delay = delay;
        }

        @Override
        public void get(CoapExchange ex) {
            ex.sendDelayedAck();
            scheduledExecutor.schedule(() -> super.get(ex), delay, TimeUnit.SECONDS);
        }
    }
}
