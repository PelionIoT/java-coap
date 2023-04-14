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
package microbenchmark;

import static java.util.concurrent.CompletableFuture.completedFuture;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import java.io.IOException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;
import protocolTests.utils.MockCoapTransport;

@State(Scope.Benchmark)
@Threads(1)
@Fork(value = 1, jvmArgsPrepend = {"-Xms128m", "-Xmx128m"})
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 10)
public class ServerBenchmark {

    private final MockCoapTransport mockTransport = new MockCoapTransport();
    private final MockCoapTransport.MockClient mockClient = mockTransport.client();
    private CoapServer server;
    private CoapPacket coapReq;
    private int i = 0;
    private long requestCounter = 0;

    @Setup
    public void setup() throws CoapException, IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        coapReq = new CoapPacket(Method.GET, MessageType.Confirmable, "/path1/sub2/sub3", null);
        coapReq.setMessageId(1234);
        coapReq.setToken(Opaque.variableUInt(0x0102030405L));
        coapReq.headers().setMaxAge(4321L);

        server = CoapServer.builder()
                .transport(mockTransport)
                .route(RouterService.builder()
                        .get("/path1/sub2/sub3", __ -> completedFuture(
                                CoapResponse.ok("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
                                        .maxAge(requestCounter++)
                        ))
                )
                .build();
        server.start();
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }


    @Benchmark
    public void handle_inbound_requests(Blackhole bh) throws InterruptedException {
        i++;
        //change MID
        coapReq.setMessageId(i & 0xFFFF);

        //send and wait for resp
        mockClient.send(coapReq);
        CoapPacket rcvdCoap = mockClient.receive();
        // assertEquals(Code.C205_CONTENT, rcvdCoap.getCode());
        bh.consume(rcvdCoap);
    }
}
