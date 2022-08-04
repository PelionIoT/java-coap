/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package org.opencoap.transport.mbedtls;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.of;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencoap.ssl.SslConfig;
import org.opencoap.ssl.transport.DtlsServer;
import org.opencoap.ssl.transport.DtlsTransmitter;

class MbedtlsCoapTransportTest {

    private SslConfig clientConf = SslConfig.client(of("test").getBytes(), of("secret").getBytes(), Collections.emptyList());
    private SslConfig serverConf = SslConfig.server(of("test").getBytes(), of("secret").getBytes(), Collections.emptyList());

    private DtlsServer dtlsServer;
    private CoapServer coapServer;
    private InetSocketAddress srvAddress;

    @BeforeEach
    void setUp() throws IOException {
        dtlsServer = DtlsServer.create(serverConf);
        coapServer = CoapServerBuilder.newBuilder()
                .transport(new MbedtlsServerCoapTransport(dtlsServer))
                .route(new RouterService.RouteBuilder()
                        .get("/test", it -> completedFuture(ok("OK!")))
                        .post("/send-malformed", it -> {
                            dtlsServer.send("acghfh".getBytes(), it.getPeerAddress());
                            return completedFuture(CoapResponse.of(Code.C201_CREATED));
                        })
                )
                .build();
        coapServer.start();
        srvAddress = new InetSocketAddress("localhost", coapServer.getLocalSocketAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        coapServer.stop();
    }

    @Test
    void shouldConnectUsingMbedtlsTransport() throws IOException, CoapException {
        // given
        MbedtlsCoapTransport clientTrans = new MbedtlsCoapTransport(DtlsTransmitter.connect(srvAddress, clientConf).join());
        CoapClient coapClient = CoapClientBuilder.newBuilder(srvAddress)
                .transport(clientTrans)
                .build();


        // when
        CoapResponse resp = coapClient.sendSync(get("/test"));

        // then
        assertEquals(ok("OK!"), resp);

        assertNotNull(clientTrans.getLocalSocketAddress());
        coapClient.close();
    }

    @Test
    void shouldIgnoreMalformedCoapPacket() throws IOException, CoapException {
        // given
        MbedtlsCoapTransport clientTrans = new MbedtlsCoapTransport(DtlsTransmitter.connect(srvAddress, clientConf).join());
        CoapClient coapClient = CoapClientBuilder.newBuilder(srvAddress)
                .transport(clientTrans)
                .build();

        // when
        assertEquals(CoapResponse.of(Code.C201_CREATED), coapClient.sendSync(post("/send-malformed")));

        // then
        assertEquals(ok("OK!"), coapClient.sendSync(get("/test")));

        coapClient.close();
    }
}