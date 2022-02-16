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
package com.mbed.coap.packet;

import static com.mbed.coap.packet.MediaTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Func;
import org.junit.jupiter.api.Test;

class CoapResponseTest {

    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(CoapResponse.class)
                .withGenericPrefabValues(Supplier.class, (Func.Func1<CompletableFuture<CoapResponse>, Supplier>) o -> () -> o)
                .withGenericPrefabValues(CompletableFuture.class, (Func.Func1<CoapResponse, CompletableFuture>) coapResponse -> new CompletableFuture<>())
                .withPrefabValues(CoapResponse.class, CoapResponse.badRequest(), CoapResponse.ok(""))
                .usingGetClass()
                .verify();
    }

    @Test
    void testToString() {
        assertEquals("CoapResponse[205, pl(4):64757061]", CoapResponse.ok("dupa").toString());
        assertEquals("CoapResponse[400, ETag:6565]", CoapResponse.badRequest().etag(Opaque.of("ee")).toString());
        assertEquals("CoapResponse[205, ContTp:0, pl(3):616161]", CoapResponse.ok("aaa", CT_TEXT_PLAIN).toString());
    }
}