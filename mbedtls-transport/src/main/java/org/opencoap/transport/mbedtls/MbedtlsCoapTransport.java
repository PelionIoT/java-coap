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
package org.opencoap.transport.mbedtls;

import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.opencoap.ssl.transport.DtlsTransmitter;
import org.opencoap.ssl.transport.Packet;
import org.opencoap.ssl.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MbedtlsCoapTransport implements CoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(MbedtlsCoapTransport.class);
    private final Transport<Packet<byte[]>> dtlsTransport;

    public MbedtlsCoapTransport(Transport<Packet<byte[]>> dtlsTransport) {
        this.dtlsTransport = dtlsTransport;
    }

    public MbedtlsCoapTransport(DtlsTransmitter dtlsTransmitter) {
        InetSocketAddress adr = dtlsTransmitter.getRemoteAddress();

        this.dtlsTransport = dtlsTransmitter.map(
                bytes -> new Packet<>(bytes, adr),
                Packet<byte[]>::getBuffer
        );
    }

    @Override
    public void start() {
        // do nothing
    }

    @Override
    public void stop() {
        try {
            dtlsTransport.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket) {
        return dtlsTransport.send(new Packet<>(coapPacket.toByteArray(), coapPacket.getRemoteAddress()));
    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return dtlsTransport.receive(Duration.ofSeconds(1)).thenCompose(this::deserialize);
    }

    private CompletableFuture<CoapPacket> deserialize(Packet<byte[]> packet) {
        if (packet.getBuffer().length > 0) {
            try {
                return completedFuture(CoapPacket.read(packet.getPeerAddress(), packet.getBuffer()));
            } catch (CoapException e) {
                LOGGER.warn("[{}] Received malformed coap. {}", packet.getPeerAddress(), e.toString());
            }
        }

        // keep waiting
        return receive();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return new InetSocketAddress("0.0." + "0.0", dtlsTransport.localPort());
    }
}
