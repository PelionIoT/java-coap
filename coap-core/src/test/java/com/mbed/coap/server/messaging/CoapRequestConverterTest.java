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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CoapRequestConverterTest {
    private CoapRequestConverter conv = new CoapRequestConverter(() -> 20);
    private Service<CoapRequest, CoapResponse> service = Mockito.mock(Service.class);

    @Test
    void shouldConvertConRequestAndResponse() {
        given(service.apply(eq(
                post(LOCAL_5683, "/test2").token(13).payload("test"))
        )).willReturn(completedFuture(
                ok("ok"))
        );

        CompletableFuture<CoapPacket> resp = conv.apply(
                newCoapPacket(LOCAL_5683).mid(1300).token(13).post().uriPath("/test2").payload("test").build(), service
        );

        assertEquals(newCoapPacket(LOCAL_5683).mid(1300).token(13).ack(Code.C205_CONTENT).payload("ok").build(), resp.join());
    }

    @Test
    void shouldConvertNonRequestAndResponse() {
        given(service.apply(eq(
                post(LOCAL_5683, "/test2").token(13).payload("test"))
        )).willReturn(completedFuture(
                ok("ok"))
        );

        CompletableFuture<CoapPacket> resp = conv.apply(
                newCoapPacket(LOCAL_5683).non().mid(1300).token(13).post().uriPath("/test2").payload("test").build(), service
        );

        assertEquals(newCoapPacket(LOCAL_5683).non(Code.C205_CONTENT).mid(20).token(13).payload("ok").build(), resp.join());
    }
}
