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

import com.mbed.coap.CoapConstants;
import com.mbed.coap.cli.providers.MbedtlsProvider;
import com.mbed.coap.cli.providers.Pair;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CoapCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapCli.class);

    public static void main(String[] args) {
        main(args, new CoapSchemes());
    }

    private static void printUsage(CoapSchemes providers) {
        System.out.println("Usage: [options...] <method> <scheme>://<host>:<port>/<uri-path> [<payload>]");
        System.out.println("Method: GET | POST | PUT | DELETE");
        System.out.println("Schemes: " + providers.supportedSchemes().replaceAll("\n", "\n         "));
        System.out.println("Options:");
        System.out.println("     -s <ssl provider>  jdk (default),");
        System.out.println("                        mbedtls (default for DTLS),");
        System.out.println("                        openssl (requires installed openssl that supports dtls),");
        System.out.println("                        stdio (standard IO)");
        System.out.println("     -k <file>          KeyStore file (with empty passphrase)");
        System.out.println("     --psk <id:hex-secret>  Pre shared key pair");
        System.out.println("     --cipher <name>    Cipher suite");
        System.out.println("     -b <block size>    Block size, one of: 16, 32, 64, 128, 256, 512, 1024");
        System.out.println("     -p <proxy>         Proxy-Uri");
        System.out.println("");
        System.out.println("Environments variables:");
        System.out.println("     COAPCLI_OPENSSL    openssl command, default: 'openssl'");
        System.out.println("");
        System.out.println("Example: GET coap://localhost:5683/small");
        System.out.println("");
    }

    public static void main(String[] args, CoapSchemes providers) {
        if (args.length < 2) {
            printUsage(providers);
            return;
        }
        main0(args, providers);
    }

    private static void main0(String[] args, CoapSchemes providers) {
        try {
            String keystoreFile = null;
            String proxyUri = null;
            BlockSize blockSize = null;
            TransportProvider transportProvider = null;
            String cipherSuite = null;
            Pair<String, Opaque> psk = null;
            int i;
            for (i = 0; i < args.length; i++) {
                if (args[i].equals("-k")) {
                    keystoreFile = args[++i];
                } else if (args[i].equals("-p")) {
                    proxyUri = args[++i];
                } else if (args[i].equals("-b")) {
                    blockSize = BlockSize.valueOf("S_" + args[++i]);
                } else if (args[i].equals("-s")) {
                    transportProvider = providers.transportProviderFor(args[++i]);
                } else if (args[i].equals("--cipher")) {
                    cipherSuite = args[++i];
                } else if (args[i].equals("--psk")) {
                    psk = Pair.split(args[++i], ':').mapValue(Opaque::decodeHex);
                } else if (args[i].charAt(0) == '-') {
                    throw new IllegalArgumentException("Unrecognised flag: " + args[i]);
                } else {
                    break;
                }
            }

            String method = args[i++];
            URI uri = URI.create(args[i++]);
            if (transportProvider == null) {
                transportProvider = ("coaps".equals(uri.getScheme())) ? new MbedtlsProvider() : providers.defaultProvider();
            }
            transportProvider.setCipherSuite(cipherSuite);

            Opaque payload = (args.length > i) ? Opaque.of(args[i]) : Opaque.EMPTY;

            new CoapCli(providers, transportProvider, keystoreFile, psk, blockSize, proxyUri, method, uri, payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error(ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.exit(0);
    }

    public CoapCli(CoapSchemes providers, TransportProvider transportProvider, String keystoreFile, Pair<String, Opaque> psk, BlockSize blockSize, String proxyUri, String method, URI uri, Opaque payload) throws IOException, InterruptedException, CoapException {

        CoapServer cliServer = providers.create(transportProvider, keystoreFile, psk, uri).blockSize(blockSize).build().start();

        InetSocketAddress destination = new InetSocketAddress(uri.getHost(), uri.getPort());
        CoapClient cli = CoapClientBuilder.clientFor(destination, cliServer);

        Thread.sleep(200);

        String uriPath = uri.getPath().isEmpty() ? CoapConstants.WELL_KNOWN_CORE : uri.getPath();
        try {
            CoapResponse resp = cli.sendSync(CoapRequest.of(destination, Method.valueOf(method), uriPath)
                    .query(uri.getQuery() == null ? "" : uri.getQuery())
                    .token(System.currentTimeMillis() % 0xFFFF)
                    .proxy(proxyUri)
                    .blockSize(blockSize)
                    .payload(payload)
            );

            if (resp.getPayload().size() > 0) {
                System.out.println();
                System.out.println(resp.getPayloadString());
            }
        } finally {
            cliServer.stop();
        }

    }


}
