/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.cli.providers;

import static com.mbed.coap.cli.CoapSchemes.*;
import com.mbed.coap.cli.TransportProvider;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import org.opencoap.ssl.SslConfig;
import org.opencoap.ssl.SslSession;
import org.opencoap.ssl.transport.DtlsTransmitter;
import org.opencoap.transport.mbedtls.MbedtlsCoapTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MbedtlsProvider extends TransportProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MbedtlsProvider.class);

    @Override
    public CoapTransport createTCP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) {
        throw new IllegalArgumentException("TLS not supported");
    }

    @Override
    public CoapTransport createUDP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks, Pair<String, Opaque> psk) throws GeneralSecurityException, IOException {
        SslConfig config;
        File fileSession;
        if (psk != null) {
            config = SslConfig.client(psk.key.getBytes(), psk.value.getBytes(), Collections.emptyList());
            fileSession = new File(psk.key + ".session");
        } else if (ks != null) {
            config = SslConfig.client(readCAs(ks));
            fileSession = new File(destAdr.getHostName() + ".session");
        } else {
            throw new IllegalArgumentException();
        }

        byte[] sessionBytes = readBytes(fileSession);

        DtlsTransmitter transport;
        if (sessionBytes.length > 0) {
            SslSession session = config.loadSession(new byte[0], sessionBytes, destAdr);
            transport = DtlsTransmitter.create(destAdr, session, 0);
        } else {
            transport = DtlsTransmitter.connect(destAdr, config, 0).join();
        }

        return new MbedtlsCoapTransport(transport) {
            @Override
            public void stop() {
                try {
                    writeBytes(fileSession, transport.saveSession());
                    LOGGER.info("Stored DTLS session into: {}", fileSession);
                } catch (IOException e) {
                    LOGGER.error("Failed to store DTLS session");
                }

                super.stop();
            }
        };
    }

    private static void writeBytes(File fileSession, byte[] bytes) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(fileSession, false);
        fileOutputStream.write(bytes);
        fileOutputStream.close();
    }

    private static byte[] readBytes(File fileSession) throws IOException {
        if (!fileSession.exists() || fileSession.length() <= 0) {
            return new byte[0];
        }

        FileInputStream fileInputStream = new FileInputStream(fileSession);
        byte[] sessionBytes = new byte[((int) fileSession.length())];
        fileInputStream.read(sessionBytes);
        return sessionBytes;
    }

}
