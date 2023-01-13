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
package org.opencoap.transport.mbedtls;

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapRequest.post;
import static com.mbed.coap.packet.CoapResponse.ok;
import static com.mbed.coap.packet.Opaque.of;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencoap.ssl.PskAuth;
import org.opencoap.ssl.SslConfig;
import org.opencoap.ssl.transport.DtlsServer;
import org.opencoap.ssl.transport.DtlsTransmitter;
import org.opencoap.ssl.transport.Packet;

class MbedtlsCoapTransportTest {

    private final SslConfig clientConf = SslConfig.client(new PskAuth("test", of("secret").getBytes()));
    private final SslConfig serverConf = SslConfig.server(new PskAuth("test", of("secret").getBytes()));

    private DtlsServer dtlsServer;
    private CoapServer coapServer;
    private InetSocketAddress srvAddress;

    @BeforeEach
    void setUp() throws IOException {
        dtlsServer = DtlsServer.create(serverConf);
        coapServer = CoapServerBuilder.newBuilder()
                .transport(new MbedtlsCoapTransport(dtlsServer))
                .route(new RouterService.RouteBuilder()
                        .get("/test", it -> completedFuture(ok("OK!")))
                        .post("/send-malformed", it -> {
                            dtlsServer.send(new Packet<>("acghfh", it.getPeerAddress()).map(String::getBytes));
                            return completedFuture(CoapResponse.of(Code.C201_CREATED));
                        })
                        .post("/auth", it -> {
                            String name = it.options().getUriQueryMap().get("name");
                            dtlsServer.setSessionAuthenticationContext(it.getPeerAddress(), name);
                            return completedFuture(CoapResponse.of(Code.C201_CREATED));
                        })
                        .get("/auth", it -> {
                            String name = it.getTransContext().get(MbedtlsCoapTransport.DTLS_CONTEXT).getAuthentication();
                            if (name != null) {
                                return completedFuture(CoapResponse.ok(name));
                            } else {
                                return completedFuture(CoapResponse.of(Code.C401_UNAUTHORIZED));
                            }

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

    @Test
    void shouldUpdateAndPassDtlsContext() throws IOException, CoapException {
        // given
        MbedtlsCoapTransport clientTrans = new MbedtlsCoapTransport(DtlsTransmitter.connect(srvAddress, clientConf).join());
        CoapClient coapClient = CoapClientBuilder.newBuilder(srvAddress)
                .transport(clientTrans)
                .build();
        // and not authenticated
        assertEquals(Code.C401_UNAUTHORIZED, coapClient.sendSync(get("/auth")).getCode());

        // when
        assertEquals(Code.C201_CREATED, coapClient.sendSync(post("/auth").query("name", "dev-007")).getCode());

        // then
        assertEquals(ok("dev-007"), coapClient.sendSync(get("/auth")));

        assertNotNull(clientTrans.getLocalSocketAddress());
        coapClient.close();
    }

}
