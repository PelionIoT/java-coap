/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.internal.CoapUdpMessaging;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author szymon
 */
public class TimeoutTest {

    @Test(expected = CoapTimeoutException.class)
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(0))
                .transport(InMemoryCoapTransport.create())
                .timeout(new SingleTimeout(100))
                .build();

        client.resource("/non/existing").sync().get();
    }

    @Test
    @Ignore
    public void timeoutTestIgn() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(61616).build();
        cnn.start();

        CoapPacket request = new CoapPacket(new InetSocketAddress(InetAddress.getLocalHost(), 60666));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        try {
            cnn.makeRequest(request).join();
            fail("Exception was expected");
        } catch (CompletionException ex) {
            //expected
        }
        assertEquals("Wrong number of transactions", 0, ((CoapUdpMessaging) cnn.getCoapMessaging()).getNumberOfTransactions());
        cnn.stop();

    }

    @Test
    public void timeoutTest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).timeout(new SingleTimeout(100)).build();
        cnn.start();

        CoapPacket request = new CoapPacket(InMemoryCoapTransport.createAddress(0));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        try {
            callback.get();
            fail("Exception was expected");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CoapException);
        }
        assertEquals("Wrong number of transactions", 0, ((CoapUdpMessaging) cnn.getCoapMessaging()).getNumberOfTransactions());
        cnn.stop();

    }
}
