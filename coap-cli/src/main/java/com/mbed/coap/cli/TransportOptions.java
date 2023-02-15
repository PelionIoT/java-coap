/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.cli.KeystoreUtils.loadKeystore;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
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
import com.mbed.coap.transport.CoapTcpTransport;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.function.Function;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

class TransportOptions {
    private Pair<String, Opaque> psk;

    @Option(names = {"-s", "--ssl-provider"}, paramLabel = "<ssl-provider>", description = "jdk (default),\nmbedtls (default for DTLS),\nopenssl (requires installed openssl that supports dtls),\nstdio (standard IO)")
    private TransportProviderTypes transportProviderType;

    @Option(names = {"-k", "--key-store"}, description = "KeyStore file (with empty passphrase)")
    private String keystoreFile;

    @Option(names = {"--cipher"}, paramLabel = "<name>", description = "Cipher suite")
    private String cipherSuite;

    @Option(names = {"-f", "--force-new-handshake"}, description = "Force new handshake, only applicable when using DTLS with CID")
    private boolean forceNewHandshake;

    @Option(names = {"--port"}, paramLabel = "<port number>", defaultValue = "0", description = "UDP source port number, default: 0")
    private int sourcePort;

    @Option(names = {"-q", "--quiet"}, paramLabel = "<name>", description = "Force new handshake, only applicable when using DTLS with CID")
    void setQuite(boolean quiet) {
        if (quiet) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger("root");
            logger.setLevel(Level.WARN);
        }
    }

    @Option(names = {"--psk"}, paramLabel = "<id:hex-secret>", description = "Pre shared key pair")
    void setPsk(String pskPair) {
        psk = Pair.split(pskPair, ':').mapValue(Opaque::decodeHex);
    }

    public final CoapServer create(URI uri, Function<CoapServerBuilder, CoapServer> configureUdp, Function<CoapServerBuilderForTcp, CoapServer> configureTcp) {
        try {
            CoapTransport transport = createTransport(uri);

            if (transport instanceof CoapTcpTransport) {
                return configureTcp.apply(TcpCoapServer.builder().transport((CoapTcpTransport) transport));
            } else {
                return configureUdp.apply(CoapServer.builder().transport(transport));
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TransportProvider createTransportProvider(TransportProviderTypes defaultTpt) {
        if (transportProviderType == null) {
            transportProviderType = defaultTpt;
        }

        switch (transportProviderType) {
            case jdk:
                return new JdkProvider();
            case openssl:
                return new OpensslProvider(cipherSuite);
            case stdio:
                return new StandardIoProvider();
            case mbedtls:
                return new MbedtlsProvider(forceNewHandshake, sourcePort);
        }
        throw new IllegalArgumentException();
    }

    private CoapTransport createTransport(URI uri) throws GeneralSecurityException, IOException {
        InetSocketAddress destAdr = addressFromUri(uri);
        KeyStore ks = loadKeystore(keystoreFile);

        switch (uri.getScheme()) {
            case "coap":
                return new PlainTextProvider(sourcePort).createUDP(CoapSerializer.UDP, destAdr, ks, psk);

            case "coap+tcp":
                return new PlainTextProvider(sourcePort).createTCP(CoapSerializer.TCP, destAdr, ks);

            case "coaps":
                return createTransportProvider(TransportProviderTypes.mbedtls)
                        .createUDP(CoapSerializer.UDP, destAdr, ks, psk);

            case "coaps+tcp":
                return createTransportProvider(TransportProviderTypes.jdk)
                        .createTCP(CoapSerializer.TCP, destAdr, ks);

            default:
                throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme());
        }
    }

    public static InetSocketAddress addressFromUri(URI uri) {
        int port = (uri.getPort() == -1) ? defaultPort(uri.getScheme()) : uri.getPort();

        return new InetSocketAddress(uri.getHost(), port);
    }

    private static int defaultPort(String scheme) {
        switch (scheme) {
            case "coap":
            case "coap+tcp":
                return 5683;

            case "coaps":
            case "coaps+tcp":
                return 5684;

            default:
                throw new IllegalArgumentException("Scheme not supported: " + scheme);
        }
    }

    enum TransportProviderTypes {
        jdk, openssl, stdio, mbedtls
    }
}
