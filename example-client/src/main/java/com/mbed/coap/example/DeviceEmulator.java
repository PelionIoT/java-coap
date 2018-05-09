/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
package com.mbed.coap.example;

import com.mbed.coap.client.RegistrationManager;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.observe.SimpleObservableResource;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SSLSocketClientTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
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
            System.out.println("Usage: {registration-uri}");
            return;
        }
        String uri = args[0];
        String keystoreFile = args.length > 1 ? args[1] : null;

        final DeviceEmulator deviceEmulator = new DeviceEmulator(uri, keystoreFile);

        Runtime.getRuntime().addShutdownHook(new Thread(deviceEmulator::stop));
    }


    public DeviceEmulator(String registrationUri, String keystoreFile) throws IOException {
        URI uri = URI.create(registrationUri);

        CoapTransport coapTransport = createTransport(keystoreFile, uri);

        emulatorServer = CoapServerBuilder.newBuilder()
                .transport(coapTransport)
                .scheduledExecutor(scheduledExecutor)
                .build()
                .start();


        //read only resources
        emulatorServer.addRequestHandler("/3/0/1", new ReadOnlyCoapResource("ARM"));
        emulatorServer.addRequestHandler("/3/0/2", new ReadOnlyCoapResource("Emulator"));
        emulatorServer.addRequestHandler("/3/0/3", new ReadOnlyCoapResource("0.0.1"));

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

    private CoapTransport createTransport(String keystoreFile, URI uri) {
        CoapTransport coapTransport;

        if (uri.getScheme().equals("coap")) {
            coapTransport = new DatagramSocketTransport(0);
        } else if (uri.getScheme().equals("coaps")) {

            SSLContext sslContext = sslContextFromKeystore(keystoreFile, "secret".toCharArray());

            coapTransport = new SSLSocketClientTransport(new InetSocketAddress(uri.getHost(), uri.getPort()), sslContext.getSocketFactory(), CoapSerializer.UDP);
        } else {
            throw new IllegalArgumentException("Protocol not supported: " + uri.getScheme());
        }
        return coapTransport;
    }

    RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    void stop() {
        registrationManager.removeRegistration();
        emulatorServer.stop();
    }


    static SSLContext sslContextFromKeystore(String resource, char[] secret) {
        try (FileInputStream f = new FileInputStream(resource)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(f, secret);

            final KeyManagerFactory kmf;
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, secret);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            //print all certificates subject
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                String certCN = ((X509Certificate) ks.getCertificate(alias)).getSubjectDN().toString();

                if (ks.isKeyEntry(alias)) {
                    LOGGER.info("Using certificate CN: " + certCN);
                } else {
                    LOGGER.info("Using trusted certificate CN: " + certCN);
                }
            }

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
