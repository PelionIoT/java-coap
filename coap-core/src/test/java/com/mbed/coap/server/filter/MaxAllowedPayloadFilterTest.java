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
package com.mbed.coap.server.filter;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.of;
import static com.mbed.coap.packet.Code.*;
import static com.mbed.coap.utils.Bytes.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Service;
import org.junit.jupiter.api.Test;

class MaxAllowedPayloadFilterTest {

    private final Service<CoapRequest, CoapResponse> service = new MaxAllowedPayloadFilter(50, "too much")
            .then(coapRequest -> completedFuture(of(C201_CREATED)));

    @Test
    void shouldPassWhenPayloadUnderLimit() {

        assertEquals(of(C201_CREATED), service.apply(post("/t").payload("1")).join());
        assertEquals(of(C201_CREATED), service.apply(post("/t").payload(opaqueOfSize(20))).join());
        assertEquals(of(C201_CREATED), service.apply(post("/t").payload(opaqueOfSize(49))).join());
        assertEquals(of(C201_CREATED), service.apply(post("/t").payload(opaqueOfSize(50))).join());

    }

    @Test
    void shouldFailWhenPayloadAboveLimit() {
        CoapResponse expected = new CoapResponse(C413_REQUEST_ENTITY_TOO_LARGE, Opaque.of("too much"), o -> o.setSize1(50));

        assertEquals(expected, service.apply(post("/t").payload(opaqueOfSize(51))).join());
        assertEquals(expected, service.apply(post("/t").payload(opaqueOfSize(10021))).join());

    }
}