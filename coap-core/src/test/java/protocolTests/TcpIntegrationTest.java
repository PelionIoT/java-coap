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
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.javassl.SingleConnectionSocketServerTransport;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import javax.net.SocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by szymon
 */
public class TcpIntegrationTest {


    private CoapServer server;
    private InetSocketAddress serverAddress;
    private CoapClient client;

    @Before
    public void setUp() throws Exception {
        server = CoapServerBuilder.newCoapServerForTcp(new SingleConnectionSocketServerTransport(0, true)).start();
        serverAddress = new InetSocketAddress("localhost", server.getLocalSocketAddress().getPort());

        client = CoapClientBuilder.clientFor(
                serverAddress,
                CoapServerBuilder.newCoapServerForTcp(new SocketClientTransport(serverAddress, SocketFactory.getDefault(), true)).start()
        );
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        server.stop();
    }

    @Test
    public void ping() throws Exception {
        assertEquals(client.ping().get().getCode(), Code.C703_PONG);
    }

    @Test
    public void request() throws Exception {
        server.addRequestHandler("/test", new ReadOnlyCoapResource("1234567890"));

        CompletableFuture<CoapPacket> resp = client.resource("/test").get();

        assertEquals("1234567890", resp.get().getPayloadString());
    }
}
