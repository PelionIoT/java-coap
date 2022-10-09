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
package microbenchmark;

import static java.util.concurrent.CompletableFuture.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.utils.AsyncQueue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@Tag("Benchmark")
public class ServerBenchmark {

    private byte[] reqData;
    private ByteBuffer buffer;
    private SynchTransportStub trans;
    private CoapServer server;
    private long stTime, endTime;
    private static final int MAX = 100;

    @BeforeEach
    public void warmUp() throws CoapException, IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        CoapPacket coapReq = new CoapPacket(Method.GET, MessageType.Confirmable, "/path1/sub2/sub3", null);
        coapReq.setMessageId(1234);
        coapReq.setToken(Opaque.variableUInt(0x0102030405L));
        coapReq.headers().setMaxAge(4321L);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        trans = new SynchTransportStub();
        server = CoapServerBuilder.newBuilder().transport(trans)
                .route(RouterService.builder()
                        .get("/path1/sub2/sub3", __ -> completedFuture(CoapResponse.ok("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")))
                )
                .build();
        server.start();
        System.out.println("MSG SIZE: " + reqData.length);
    }

    @AfterEach
    public void coolDown() {
        System.out.println(String.format("RUN-TIME: %dms, MSG-PER-SEC: %d", (endTime - stTime), (MAX * 1000 / (endTime - stTime))));
        server.stop();
    }

    @Test
    public void server_100k() throws InterruptedException {
        stTime = System.currentTimeMillis();
        for (int i = 0; i < MAX; i++) {
            //change MID
            reqData[2] = (byte) (i >> 8);
            reqData[3] = (byte) (i & 0xFF);
            //send and wait for resp
            trans.receive(buffer);
        }
        endTime = System.currentTimeMillis();
    }

    private static class SynchTransportStub extends BlockingCoapTransport {

        private final InetSocketAddress addr = new InetSocketAddress("localhost", 5683);
        private final AsyncQueue<CoapPacket> receiveQueue = new AsyncQueue<>();
        private boolean sendCalled = false;

        @Override
        public void start() throws IOException {
        }

        @Override
        public void stop() {
            receiveQueue.removeAll();
        }

        @Override
        public synchronized void sendPacket0(CoapPacket coapPacket) {
            sendCalled = true;
            this.notifyAll();
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return addr;
        }

        public synchronized void receive(ByteBuffer data) throws InterruptedException {
            try {
                receiveQueue.add(CoapPacket.read(addr, data.array(), data.position()));
            } catch (CoapException e) {
                throw new RuntimeException(e);
            }
            while (!sendCalled) {
                this.wait();
            }
        }

        @Override
        public CompletableFuture<CoapPacket> receive() {
            return receiveQueue.poll();
        }
    }
}
