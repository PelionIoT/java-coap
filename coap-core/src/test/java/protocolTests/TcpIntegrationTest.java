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
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.javassl.SingleConnectionSocketServerTransport;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.net.SocketFactory;
import org.junit.After;
import org.junit.Test;

/**
 * Created by szymon
 */
public class TcpIntegrationTest {

    private CoapServer server;
    private InetSocketAddress serverAddress;
    private CoapClient client;

    private void initClient() throws IOException {
        initClient(b -> {
        });
    }

    private void initClient(Consumer<CoapServerBuilder.CoapServerBuilderForTcp> b) throws IOException {
        CoapServerBuilder.CoapServerBuilderForTcp builder = CoapServerBuilder.newBuilderForTcp()
                .transport(new SocketClientTransport(serverAddress, SocketFactory.getDefault(), true))
                .blockSize(BlockSize.S_1024_BERT)
                .maxMessageSize(1_200);

        b.accept(builder);

        client = CoapClientBuilder.clientFor(
                serverAddress,
                builder.start()
        );
    }

    private void initServer() throws IOException {
        server = CoapServerBuilder
                .newBuilderForTcp()
                .transport(new SingleConnectionSocketServerTransport(0, true))
                .blockSize(BlockSize.S_1024_BERT)
                .maxMessageSize(10_000)
                .start();

        serverAddress = new InetSocketAddress("localhost", server.getLocalSocketAddress().getPort());
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        server.stop();
    }

    @Test
    public void ping() throws Exception {
        initServer();
        initClient();

        assertEquals(client.ping().get().getCode(), Code.C703_PONG);
    }

    @Test
    public void request() throws Exception {
        initServer();
        initClient();
        server.addRequestHandler("/test", new ReadOnlyCoapResource("1234567890"));

        CompletableFuture<CoapPacket> resp = client.resource("/test").get();

        assertEquals("1234567890", resp.get().getPayloadString());
    }

    @Test
    public void request_withLargePayloadInResponse_blocks() throws Exception {
        initServer();
        initClient();
        server.addRequestHandler("/test", new ReadOnlyCoapResource("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_"));

        //unfortunately, need to wait until capabilities is exchanged
        Thread.sleep(80);
        CompletableFuture<CoapPacket> resp = client.resource("/test").get();

        assertEquals(1300, resp.get().getPayload().length);
        //verify that blocks was used
        assertNotNull(resp.get().headers().getBlock2Res());
    }

    @Test
    public void request_withLargePayloadInResponse_clientDoesNotSupportBlocks() throws Exception {
        initServer();
        initClient(builder -> builder
                .blockSize(null)
                .maxMessageSize(1300)
        );

        server.addRequestHandler("/test", new ReadOnlyCoapResource("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789_"));

        CompletableFuture<CoapPacket> resp = client.resource("/test").get();

        assertEquals(1300, resp.get().getPayload().length);
        //block was not used
        assertNull(resp.get().headers().getBlock2Res());
    }

}
