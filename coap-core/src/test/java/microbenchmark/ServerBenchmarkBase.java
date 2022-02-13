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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;


/**
 * @author szymon
 */
public abstract class ServerBenchmarkBase {

    FloodTransportStub trans;
    protected int MAX = 1000000;
    protected ExecutorService executor;
    //
    private byte[] reqData;
    private ByteBuffer buffer;
    private CoapServer server;
    private long stTime, endTime;

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

        server = CoapServerBuilder.newBuilder().transport(trans).duplicateMsgCacheSize(10000).build();
        server.addRequestHandler("/path1/sub2/sub3", new ReadOnlyCoapResource("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        server.start();
        System.out.println("MSG SIZE: " + reqData.length);
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        //Thread.sleep(4000);
    }

    @AfterEach
    public void coolDown() {
        System.out.println("RUN-TIME: " + (endTime - stTime) + "ms, MSG-PER-SEC: " + (MAX * 1000L / (endTime - stTime)));
        server.stop();
    }

    @Test
    public void server_multi_1000k() throws InterruptedException {
        stTime = System.currentTimeMillis();
        int mid = 0;
        for (int i = 0; i < MAX; i++) {
            //change MID
            reqData[2] = (byte) (mid >> 8);
            reqData[3] = (byte) (mid & 0xFF);

            if (trans.receive(buffer)) {
                mid++;
            }
        }
        trans.LATCH.await();
        endTime = System.currentTimeMillis();
    }

    static class FloodTransportStub extends BlockingCoapTransport {

        private CoapReceiver udpReceiver;
        private final Executor executor;

        private final InetSocketAddress[] addrArr;
        final CountDownLatch LATCH;

        public FloodTransportStub(int max, ExecutorService executor) {
            this(max, (int) Math.ceil(max / (double) 0xFFFF), executor); //no message duplication guaranted
        }

        public FloodTransportStub(int max, int maxAddr, ExecutorService executor) {
            this.LATCH = new CountDownLatch(max);
            addrArr = new InetSocketAddress[maxAddr];
            for (int i = 0; i < maxAddr; i++) {
                addrArr[i] = new InetSocketAddress("localhost", 5684 + i);
            }
            this.executor = executor;
        }

        @Override
        public void start(CoapReceiver coapReceiver) throws IOException {
            this.udpReceiver = coapReceiver;
        }

        @Override
        public void stop() {
            this.udpReceiver = null;
        }

        @Override
        public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
            LATCH.countDown();
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return addrArr[0];
        }

        private int addIndex = 0;

        public boolean receive(ByteBuffer data) {
            byte[] packetData = new byte[data.position()];
            System.arraycopy(data.array(), 0, packetData, 0, packetData.length);
            InetSocketAddress adr = addrArr[addIndex++ % addrArr.length];

            executor.execute(() -> {
                try {
                    udpReceiver.handle(CoapPacket.read(adr, packetData), TransportContext.NULL);
                } catch (CoapException e) {
                    throw new RuntimeException(e);
                }
            });

            return addIndex % addrArr.length == 0;
        }
    }
}
