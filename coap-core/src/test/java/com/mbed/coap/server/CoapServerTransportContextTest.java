/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.server;

import static com.mbed.coap.packet.CoapRequest.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class CoapServerTransportContextTest {

    private CoapServer server;
    private final CoapResourceTest coapResourceTest = new CoapResourceTest();
    private final InMemoryCoapTransport srvTransport = spy(new InMemoryCoapTransport(5683));

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder()
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
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(5683)).transport(cliTransport).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        client.sendSync(get("/test").context(new TextTransportContext("client-sending")));
        assertEquals("dupa", ((TextTransportContext) coapResourceTest.transportContext).getText());
        verify(cliTransport).sendPacket(argThat(cp ->
                cp.getTransportContext().equals(new TextTransportContext("client-sending"))
        ));
        // verify(srvTransport).sendPacket(isA(CoapPacket.class), isA(InetSocketAddress.class), eq(new TextTransportContext("get-response")));

        srvTransport.setTransportContext(new TextTransportContext("dupa2"));
        client.sendSync(get("/test"));
        assertEquals("dupa2", ((TextTransportContext) coapResourceTest.transportContext).getText());

        client.close();
    }

    @Test
    public void testRequestWithBlocks() throws IOException, CoapException {
        InMemoryCoapTransport cliTransport = spy(new InMemoryCoapTransport());
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(5683)).transport(cliTransport).blockSize(BlockSize.S_16).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        CoapResponse resp = client.sendSync(put("/test").payload("fhdkfhsdkj fhsdjkhfkjsdh fjkhs dkjhfsdjkh")
                .context(new TextTransportContext("client-block")));

        assertEquals(Code.C201_CREATED, resp.getCode());
        assertEquals("dupa", ((TextTransportContext) coapResourceTest.transportContext).getText());

        //for each block it sends same transport context
        verify(cliTransport, times(3)).sendPacket(argThat(cp ->
                cp.getTransportContext().equals(new TextTransportContext("client-block"))
        ));

        client.close();
    }

    private static class TextTransportContext implements TransportContext {

        private final String text;

        public TextTransportContext(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TextTransportContext)) {
                return false;
            }

            TextTransportContext that = (TextTransportContext) o;

            if (!text.equals(that.text)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }

        @Override
        public Object get(Object key) {
            return null;
        }
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
