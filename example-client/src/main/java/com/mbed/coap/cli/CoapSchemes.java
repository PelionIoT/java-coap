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

import com.mbed.coap.cli.providers.JdkProvider;
import com.mbed.coap.cli.providers.MbedtlsProvider;
import com.mbed.coap.cli.providers.OpensslProvider;
import com.mbed.coap.cli.providers.Pair;
import com.mbed.coap.cli.providers.PlainTextProvider;
import com.mbed.coap.cli.providers.StandardIoProvider;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.CoapServerBuilderForTcp;
import com.mbed.coap.server.TcpCoapServer;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class CoapSchemes {
    public static char[] secret() {
        return "".toCharArray();
    }

    public final CoapServer create(TransportProvider transportProvider, String keystoreFile, Pair<String, Opaque> psk, URI uri,
            Function<CoapServerBuilder, CoapServer> configureUdp,
            Function<CoapServerBuilderForTcp, CoapServer> configureTcp) {

        try {
            return create(transportProvider, loadKeystore(keystoreFile), psk, uri, configureUdp, configureTcp);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String supportedSchemes() {
        return "coap, coap+tcp, coaps, coaps+tcp, coaps+tcp-d2 (draft 2)";
    }

    private CoapServer create(TransportProvider transportProvider, KeyStore ks, Pair<String, Opaque> psk, URI uri,
            Function<CoapServerBuilder, CoapServer> configureUdp,
            Function<CoapServerBuilderForTcp, CoapServer> configureTcp) throws GeneralSecurityException, IOException {
        InetSocketAddress destAdr = addressFromUri(uri);

        switch (uri.getScheme()) {
            case "coap":
                return configureUdp.apply(
                        CoapServer.builder()
                                .transport(new PlainTextProvider().createUDP(CoapSerializer.UDP, destAdr, ks, psk))
                );

            case "coap+tcp":
                return configureTcp.apply(
                        TcpCoapServer.builder()
                                .transport(new PlainTextProvider().createTCP(CoapSerializer.TCP, destAdr, ks))
                );

            case "coaps":
                return configureUdp.apply(
                        CoapServer.builder()
                                .transport(transportProvider.createUDP(CoapSerializer.UDP, destAdr, ks, psk))
                );

            case "coaps+tcp":
                return configureTcp.apply(TcpCoapServer.builder()
                        .transport(transportProvider.createTCP(CoapSerializer.TCP, destAdr, ks))
                );

            case "coaps+tcp-d2":
                return configureUdp.apply(
                        CoapServer.builder()
                                .transport(transportProvider.createTCP(CoapSerializer.UDP, destAdr, ks))
                );

            default:
                throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme());
        }
    }

    public static InetSocketAddress addressFromUri(URI uri) {
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
            case "mbedtls":
                return new MbedtlsProvider();
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

    public static List<X509Certificate> readCAs(KeyStore ks) throws KeyStoreException {
        List<X509Certificate> certs = new LinkedList<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) {
                certs.add((X509Certificate) ks.getCertificate(alias));
            }
        }

        return certs;
    }

}
