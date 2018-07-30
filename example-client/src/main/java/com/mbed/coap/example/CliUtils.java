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
package com.mbed.coap.example;

import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SSLSocketClientTransport;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CliUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CliUtils.class);

    static CoapServerBuilder builderFrom(String keystoreFile, URI uri) {
        SSLContext sslContext;

        switch (uri.getScheme()) {
            case "coap":
                return CoapServerBuilder.newBuilder().transport(new DatagramSocketTransport(0));

            case "coaps":
                sslContext = sslContextFromKeystore(keystoreFile, "secret".toCharArray());
                return CoapServerBuilder.newBuilder().transport(
                        new SSLSocketClientTransport(new InetSocketAddress(uri.getHost(), uri.getPort()), sslContext.getSocketFactory(), CoapSerializer.UDP, true)
                );

            case "coaps+tcp":
                sslContext = sslContextFromKeystore(keystoreFile, "secret".toCharArray());
                return CoapServerBuilder.newBuilderForTcp().transport(
                        new SSLSocketClientTransport(new InetSocketAddress(uri.getHost(), uri.getPort()), sslContext.getSocketFactory(), CoapSerializer.TCP, true)
                );

            case "coap+tcp":
                return CoapServerBuilder.newBuilderForTcp().transport(
                        new SocketClientTransport(new InetSocketAddress(uri.getHost(), uri.getPort()), SocketFactory.getDefault(), CoapSerializer.TCP, true)
                );

            default:
                throw new IllegalArgumentException("Protocol not supported: " + uri.getScheme());
        }
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
                    LOGGER.info("Using certificate: " + certCN);
                } else {
                    LOGGER.info("Using trusted certificate: " + certCN);
                }
            }

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
