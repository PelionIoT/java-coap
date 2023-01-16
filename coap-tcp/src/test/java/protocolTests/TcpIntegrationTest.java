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
package protocolTests;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.server.TcpCoapServer;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SingleConnectionSocketServerTransport;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.net.SocketFactory;
import org.junit.jupiter.api.Test;


public class TcpIntegrationTest extends IntegrationTestBase {

    @Override
    protected CoapServer buildServer(int port, Service<CoapRequest, CoapResponse> route) throws IOException {
        return TcpCoapServer.builder()
                .transport(new SingleConnectionSocketServerTransport(port, CoapSerializer.TCP))
                .blockSize(BlockSize.S_1024_BERT)
                .maxMessageSize(100_000)
                .route(route)
                .build();
    }

    @Override
    protected CoapClient buildClient(int port) throws IOException {
        InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);

        return TcpCoapServer.builder()
                .transport(new SocketClientTransport(serverAddress, SocketFactory.getDefault(), CoapSerializer.TCP, true))
                .blockSize(BlockSize.S_1024_BERT)
                .maxIncomingBlockTransferSize(4000)
                .maxMessageSize(2100)
                .buildClient(serverAddress);
    }

    @Override
    public void sendPing() throws Exception {
        Boolean pingResp = client.ping().get();

        assertTrue(pingResp);
    }

    @Test
    public void reconnect() throws Exception {
        int port = server.getLocalSocketAddress().getPort();
        assertTrue(client.ping().get());

        server.stop();

        server = buildServer(port, RouterService.builder().build()).start();

        await().ignoreExceptions().untilAsserted(() ->
                assertTrue(client.ping().get())
        );
    }


}
