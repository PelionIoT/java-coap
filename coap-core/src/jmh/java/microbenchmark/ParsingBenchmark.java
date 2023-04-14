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

import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Threads(1)
@Fork(value = 1, jvmArgsPrepend = {"-Xms128m", "-Xmx128m"})
@Warmup(iterations = 1, time = 3)
@Measurement(iterations = 1, time = 10)
public class ParsingBenchmark {

    private final CoapPacket packet = createCoapPacket();
    private final CoapPacket simpleCoap = newCoapPacket().emptyAck(5154);
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public static CoapPacket createCoapPacket() {
        // ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        CoapPacket packet = new CoapPacket(Method.GET, MessageType.Confirmable, "/path-pppppppppppppppppp1/path-dddddddddd-2/dfdshffsdkjfhsdks3/444444444444444444444444444444444444444/55555555555555555555555555555555555555555555555", null);
        packet.setMessageId(1234);
        packet.setToken(Opaque.variableUInt(0x0102030405L));
        packet.headers().setMaxAge(4321L);
        packet.headers().setEtag(new Opaque(new byte[]{89, 10, 31, 7, 1}));
        packet.headers().setObserve(9876);
        packet.headers().setBlock1Req(new BlockOption(13, BlockSize.S_16, true));
        packet.headers().setContentFormat(MediaTypes.CT_APPLICATION_XML);
        packet.headers().setLocationPath("/1/222/33333/4444444/555555555555555555555555");
        packet.headers().setUriQuery("ppar=val1&par222222222222222222222=val2222222222222222222222222222222222");
        packet.setPayload("<k>12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890</k>");

        // System.out.println("MSG SIZE: " + packet.toByteArray().length);
        return packet;
    }

    @Benchmark()
    public void serialize_complex_coap(Blackhole bh) throws CoapException {
        baos.reset();
        packet.writeTo(baos);

        CoapPacket packet2 = CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));

        bh.consume(packet2);
    }

    @Benchmark()
    public void serialize_simple_coap(Blackhole bh) throws CoapException {
        baos.reset();

        simpleCoap.writeTo(baos);

        CoapPacket packet2 = CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));

        bh.consume(packet2);
    }
}
