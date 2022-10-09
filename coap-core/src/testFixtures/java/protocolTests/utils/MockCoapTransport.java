/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package protocolTests.utils;

import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.AsyncQueue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class MockCoapTransport extends BlockingCoapTransport {

    private final BlockingQueue<CoapPacket> sentPackets = new ArrayBlockingQueue<>(100);
    private final AsyncQueue<CoapPacket> receiveQueue = new AsyncQueue<>();

    @Override
    public void start() throws IOException {
    }

    @Override
    public void stop() {
        receiveQueue.removeAll();
    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return receiveQueue.poll();
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket) {
        sentPackets.add(coapPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return new InetSocketAddress(5683);
    }

    public MockClient client() {
        return new MockClient();
    }

    public class MockClient {
        public void send(CoapPacket packet) {
            receiveQueue.add(packet);
        }

        public void send(CoapPacketBuilder packetBuilder) {
            send(packetBuilder.build());
        }

        public CoapPacket receive() throws InterruptedException {
            return sentPackets.poll(1, TimeUnit.SECONDS);
        }

        public void verifyReceived(CoapPacketBuilder packetBuilder) throws InterruptedException {
            verifyReceived(packetBuilder.build());
        }

        public void verifyReceived(CoapPacket packet) throws InterruptedException {
            CoapPacket received = sentPackets.poll(1, TimeUnit.SECONDS);
            assertNotNull(received);
            received.setTransportContext(TransportContext.EMPTY);

            assertEquals(packet, received);
        }

        public void verifyReceived() throws InterruptedException {
            CoapPacket received = sentPackets.poll(1, TimeUnit.SECONDS);
            assertNotNull(received);
        }

        public boolean nothingReceived() {
            return sentPackets.isEmpty();
        }
    }
}
