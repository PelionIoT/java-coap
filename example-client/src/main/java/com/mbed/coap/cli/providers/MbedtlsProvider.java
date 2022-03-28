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

import com.mbed.coap.cli.TransportProvider;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import org.opencoap.ssl.DatagramChannelTransport;
import org.opencoap.ssl.SslConfig;
import org.opencoap.ssl.SslHandshakeContext;
import org.opencoap.ssl.SslSession;
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
        if (psk == null) {
            throw new IllegalArgumentException("Only PSK authentication is supported");
        }

        SslConfig config = SslConfig.Companion.client(psk.key.getBytes(), psk.value.getBytes(), Collections.emptyList());
        DatagramChannel channel = DatagramChannel.open().connect(destAdr);
        DatagramChannelTransport transport = new DatagramChannelTransport(channel, destAdr);

        SslHandshakeContext sslHandshake = config.newContext(transport);

        long beforeTs = System.currentTimeMillis();
        SslSession session = sslHandshake.handshake().join();
        LOGGER.info("[{}] Connected in {}ms [cipher-suite:{}]", destAdr, System.currentTimeMillis() - beforeTs, session.getCipherSuite());

        return new MbedtlsCoapTransport(session, destAdr, channel);
    }

    static class MbedtlsCoapTransport extends BlockingCoapTransport {

        private volatile CoapReceiver receiver;
        private final SslSession session;
        private final InetSocketAddress destAdr;
        private final DatagramChannel channel;

        MbedtlsCoapTransport(SslSession session, InetSocketAddress destAdr, DatagramChannel channel) {
            this.session = session;
            this.destAdr = destAdr;
            this.channel = channel;

            // keep reading in async loop
            readNext();
        }

        private void readNext() {
            session.read()
                    .thenApply(bytes -> {
                        try {
                            return CoapPacket.read(destAdr, bytes);
                        } catch (CoapException e) {
                            throw new CompletionException(e);
                        }
                    })
                    .thenAccept(coap -> {
                        receiver.handle(coap, TransportContext.EMPTY);
                        readNext();
                    });
        }

        @Override
        public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
            session.send(coapPacket.toByteArray());
        }

        @Override
        public void start(CoapReceiver coapReceiver) throws IOException {
            receiver = coapReceiver;
        }

        @Override
        public void stop() {
            try {
                channel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return destAdr;
        }
    }
}
