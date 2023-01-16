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
package microbenchmark;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@Tag("Benchmark")
public class ServerNotifBenchmark {

    ServerBenchmarkBase.FloodTransportStub trans;
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

        CoapPacket coapReq = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, null);
        coapReq.setMessageId(1234);
        coapReq.setToken(Opaque.variableUInt(0x0102030405L));
        coapReq.headers().setMaxAge(4321L);
        coapReq.headers().setObserve(6328);
        reqData = coapReq.toByteArray();

        buffer = ByteBuffer.wrap(reqData);
        buffer.position(coapReq.toByteArray().length);

        executor = Executors.newScheduledThreadPool(1);
        trans = new ServerBenchmarkBase.FloodTransportStub(MAX, executor);
        server = CoapServer.builder().transport(trans).duplicateMsgCacheSize(10000).build();
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
    public void notificatins_1000k() throws InterruptedException {
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

}
