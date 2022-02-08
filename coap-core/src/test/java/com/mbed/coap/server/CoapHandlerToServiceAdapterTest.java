/**
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
package com.mbed.coap.server;

import static java.util.concurrent.CompletableFuture.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.transport.TransportContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CoapHandlerToServiceAdapterTest {

    private CoapExchange exchange = Mockito.mock(CoapExchange.class);

    @Test
    public void sendResponse() throws CoapException {
        CoapHandlerToServiceAdapter adapter = new CoapHandlerToServiceAdapter(req -> completedFuture(CoapResponse.ok("test")));

        given(exchange.getRequestTransportContext()).willReturn(TransportContext.NULL);
        given(exchange.getRequest()).willReturn(newCoapPacket(1).get().uriPath("/test").build());

        // when
        adapter.handle(exchange);

        // then
        verify(exchange).setResponse(newCoapPacket(1).ack(Code.C205_CONTENT).payload("test").build());
        verify(exchange).sendResponse();
    }

}