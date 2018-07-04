/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class ParsingBenchmark {

    long stTime, endTime;

    private CoapPacket packet;

    @Before
    public void warmUp() throws CoapException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        packet = new CoapPacket(Method.GET, MessageType.Confirmable, "/path-pppppppppppppppppp1/path-dddddddddd-2/dfdshffsdkjfhsdks3/444444444444444444444444444444444444444/55555555555555555555555555555555555555555555555", null);
        packet.setMessageId(1234);
        packet.setToken(new byte[]{1, 2, 3, 4, 5});
        packet.headers().setMaxAge(4321L);
        packet.headers().setEtag(new byte[]{89, 10, 31, 7, 1});
        packet.headers().setObserve(9876);
        packet.headers().setBlock1Req(new BlockOption(13, BlockSize.S_16, true));
        packet.headers().setContentFormat(MediaTypes.CT_APPLICATION_XML);
        packet.headers().setLocationPath("/1/222/33333/4444444/555555555555555555555555");
        packet.headers().setUriQuery("ppar=val1&par222222222222222222222=val2222222222222222222222222222222222");
        packet.setPayload("<k>12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890</k>");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        packet.writeTo(baos);
        CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));

        System.out.println("MSG SIZE: " + packet.toByteArray().length);
    }

    @After
    public void coolDown() {
        System.out.println("TIME: " + (endTime - stTime) + " MSG-PER-SEC: " + (1000000 / ((endTime - stTime))) + "k");
    }

    @Test
    public void parsing_1000k() throws CoapException {
        stTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //ByteOutputStream baos = new ByteOutputStream();
            packet.writeTo(baos);

            CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));
            //CoapPacket.deserialize(new ByteInputStream(baos));
        }
        endTime = System.currentTimeMillis();
    }

    //  MICRO-BENCHMARK RESULTS
    //----------------------------------
    //CPU:                Intel Core i5
    //Iterations:               100 000
    //----------------------------------
    //ByteOutputStream:         2717 ms
    //ByteArrayOutputStream:    3205 ms
    //----------------------------------
}
