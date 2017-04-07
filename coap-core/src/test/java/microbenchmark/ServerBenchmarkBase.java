/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.SimpleCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


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

    @Before
    public void warmUp() throws CoapException, IOException {
        LogManager.getRootLogger().setLevel(Level.ERROR);
        CoapPacket coapReq = new CoapPacket(Method.GET, MessageType.Confirmable, "/path1/sub2/sub3", null);
        coapReq.setMessageId(1234);
        coapReq.setToken(new byte[]{1, 2, 3, 4, 5});
        coapReq.headers().setMaxAge(4321L);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        server = CoapServerBuilder.newBuilder().transport(trans).duplicateMsgCacheSize(10000).build();
        server.addRequestHandler("/path1/sub2/sub3", new SimpleCoapResource("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        server.start();
        System.out.println("MSG SIZE: " + reqData.length);
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        //Thread.sleep(4000);
    }

    @After
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

    static class FloodTransportStub implements CoapTransport {

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
        public void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
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
