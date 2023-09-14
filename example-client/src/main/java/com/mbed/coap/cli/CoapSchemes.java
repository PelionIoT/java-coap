/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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

import com.mbed.coap.cli.providers.JdkProvider;
import com.mbed.coap.cli.providers.OpensslProvider;
import com.mbed.coap.cli.providers.PlainTextProvider;
import com.mbed.coap.cli.providers.StandardIoProvider;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;

public class CoapSchemes {
    public static char[] secret() {
        return "secret".toCharArray();
    }

    public final CoapServerBuilder create(TransportProvider transportProvider, String keystoreFile, URI uri) {

        try {
            return create(transportProvider, loadKeystore(keystoreFile), uri);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String supportedSchemes() {
        return "coap, coap+tcp, coaps, coaps+tcp, coaps+tcp-d2 (draft 2)";
    }

    protected CoapServerBuilder create(TransportProvider transportProvider, KeyStore ks, URI uri) throws GeneralSecurityException, IOException {
        InetSocketAddress destAdr = addressFromUri(uri);

        switch (uri.getScheme()) {
            case "coap":
                return CoapServerBuilder.newBuilder()
                        .transport(new PlainTextProvider().createUDP(CoapSerializer.UDP, destAdr, ks));

            case "coap+tcp":
                return CoapServerBuilder.newBuilderForTcp()
                        .transport(new PlainTextProvider().createTCP(CoapSerializer.TCP, destAdr, ks));

            case "coaps":
                return CoapServerBuilder.newBuilder()
                        .transport(transportProvider.createUDP(CoapSerializer.UDP, destAdr, ks));

            case "coaps+tcp":
                return CoapServerBuilder.newBuilderForTcp()
                        .transport(transportProvider.createTCP(CoapSerializer.TCP, destAdr, ks));

            case "coaps+tcp-d2":
                return CoapServerBuilder.newBuilder()
                        .transport(transportProvider.createTCP(CoapSerializer.UDP, destAdr, ks));

            default:
                throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme());
        }
    }

    protected static InetSocketAddress addressFromUri(URI uri) {
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    private static KeyStore loadKeystore(String keystoreFile) {
        KeyStore ks = null;
        if (keystoreFile != null) {
            try (FileInputStream f = new FileInputStream(keystoreFile)) {
                ks = KeyStore.getInstance("JKS");
                ks.load(f, secret());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return ks;
    }

    public TransportProvider transportProviderFor(String transport) {
        switch (transport.toLowerCase()) {
            case "jdk":
                return new JdkProvider();
            case "openssl":
                return new OpensslProvider();
            case "stdio":
                return new StandardIoProvider();
            default:
                throw new IllegalArgumentException("Not supported transport: " + transport);
        }
    }

    public TransportProvider defaultProvider() {
        return new JdkProvider();
    }

    public static String findKeyAlias(KeyStore ks) throws KeyStoreException {
        ArrayList<String> aliases = Collections.list(ks.aliases());

        for (String alias : aliases) {
            if (ks.isKeyEntry(alias) && !"ca".equals(alias)) {
                return alias;
            }
        }
        return null;
    }

}
