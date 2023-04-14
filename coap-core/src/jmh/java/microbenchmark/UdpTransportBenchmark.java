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

import static com.mbed.coap.utils.Bytes.opaqueOfRandom;
import static com.mbed.coap.utils.Networks.localhost;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;

@State(Scope.Benchmark)
@Threads(1)
@Fork(value = 1, jvmArgsPrepend = {"-Xms128m", "-Xmx128m"})
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 1, time = 10)
public class UdpTransportBenchmark {

    private CoapServer coapServer;
    private DatagramSocketTransport server = DatagramSocketTransport.udp(1_5683);
    private DatagramSocketTransport client = DatagramSocketTransport.udp(2_5683);
    private CoapPacket coapRequest = newCoapPacket(localhost(1_5683)).mid(313).get().uriPath("/test").build();
    private CoapPacket coapResp = newCoapPacket(localhost(2_5683)).mid(313).ack(Code.C205_CONTENT).payload(opaqueOfRandom(1024)).build();

    @Setup
    public void setup() throws CoapException, IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        coapServer = new CoapServer(server, coapIn -> server.sendPacket(coapResp), null, () -> {
        });
        coapServer.start();

        client.start();
    }

    @TearDown
    public void tearDown() {
        client.stop();
        coapServer.stop();
    }


    @Benchmark
    @OperationsPerInvocation(1)
    public void udpTransport_1_outgoing_transaction(Blackhole bh) throws InterruptedException {
        assertTrue(client.sendPacket(coapRequest).join());

        CompletableFuture<CoapPacket> cliReceived = client.receive();
        assertNotNull(cliReceived.join());
        // assertEquals(Code.C205_CONTENT, cliReceived.join().getCode());

        bh.consume(cliReceived);
    }

    private CompletableFuture<CoapPacket>[] cliReceived = new CompletableFuture[20];

    @Benchmark
    @OperationsPerInvocation(20)
    public void udpTransport_20_outgoing_transactions(Blackhole bh) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            assertTrue(client.sendPacket(coapRequest).join());
            cliReceived[i] = client.receive();
        }

        for (int i = 0; i < 20; i++) {
            assertNotNull(cliReceived[i].join());
            // assertEquals(Code.C205_CONTENT, cliReceived.join().getCode());

            bh.consume(cliReceived[i]);
        }
    }

}
