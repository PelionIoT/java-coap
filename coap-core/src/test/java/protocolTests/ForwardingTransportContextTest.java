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

import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapRequest.put;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.ObservableResourceService;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class ForwardingTransportContextTest {

    private CoapServer server;
    private final CoapResourceTest coapResourceTest = new CoapResourceTest();
    private final InMemoryCoapTransport srvTransport = spy(new InMemoryCoapTransport(5683));
    private final TransportContext.Key<String> MY_TEXT = new TransportContext.Key<>("");

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServer.builder()
                .route(RouterService.builder()
                        .get("/test", coapResourceTest)
                        .put("/test", coapResourceTest)
                        .get("/obs", new ObservableResourceService(CoapResponse.ok("A")))
                )
                .blockSize(BlockSize.S_16).transport(srvTransport).build();

        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testRequest() throws IOException, CoapException {
        InMemoryCoapTransport cliTransport = spy(new InMemoryCoapTransport());
        CoapClient client = CoapServer.builder().transport(cliTransport).buildClient(InMemoryCoapTransport.createAddress(5683));

        srvTransport.setTransportContext(TransportContext.of(MY_TEXT, "dupa"));
        client.sendSync(get("/test").context(TransportContext.of(MY_TEXT, "client-sending")));
        assertEquals("dupa", coapResourceTest.transportContext.get(MY_TEXT));
        verify(cliTransport).sendPacket(argThat(cp ->
                cp.getTransportContext().get(MY_TEXT).equals("client-sending")
        ));
        // verify(srvTransport).sendPacket(isA(CoapPacket.class), isA(InetSocketAddress.class), eq(new TextTransportContext("get-response")));

        srvTransport.setTransportContext(TransportContext.of(MY_TEXT, "dupa2"));
        client.sendSync(get("/test"));
        assertEquals("dupa2", coapResourceTest.transportContext.get(MY_TEXT));

        client.close();
    }

    @Test
    public void testRequestWithBlocks() throws IOException, CoapException {
        InMemoryCoapTransport cliTransport = spy(new InMemoryCoapTransport());
        CoapClient client = CoapServer.builder().transport(cliTransport).blockSize(BlockSize.S_16).buildClient(InMemoryCoapTransport.createAddress(5683));

        srvTransport.setTransportContext(TransportContext.of(MY_TEXT, "dupa"));
        CoapResponse resp = client.sendSync(put("/test").payload("fhdkfhsdkj fhsdjkhfkjsdh fjkhs dkjhfsdjkh")
                .context(TransportContext.of(MY_TEXT, "client-block")));

        assertEquals(Code.C201_CREATED, resp.getCode());
        assertEquals("dupa", coapResourceTest.transportContext.get(MY_TEXT));

        //for each block it sends same transport context
        verify(cliTransport, times(3)).sendPacket(argThat(cp ->
                cp.getTransportContext().get(MY_TEXT).equals("client-block")
        ));

        client.close();
    }


    private static class CoapResourceTest implements Service<CoapRequest, CoapResponse> {

        TransportContext transportContext;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            switch (req.getMethod()) {
                case GET:
                    transportContext = req.getTransContext();
                    return completedFuture(CoapResponse.of(Code.C205_CONTENT));

                case PUT:
                    transportContext = req.getTransContext();
                    return completedFuture(CoapResponse.of(Code.C201_CREATED));
            }
            throw new IllegalStateException();
        }

    }
}
