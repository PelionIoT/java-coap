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

import com.mbed.coap.transport.TransportContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Func;
import org.junit.jupiter.api.Test;

class SeparateResponseTest {

    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(SeparateResponse.class)
                .withGenericPrefabValues(Supplier.class, (Func.Func1<CompletableFuture<CoapResponse>, Supplier>) o -> () -> o)
                .withGenericPrefabValues(CompletableFuture.class, (Func.Func1<CoapResponse, CompletableFuture>) coapResponse -> new CompletableFuture<>())
                .withPrefabValues(CoapResponse.class, CoapResponse.badRequest(), CoapResponse.ok(""))
                .withPrefabValues(TransportContext.class, TransportContext.EMPTY, TransportContext.of(TransportContext.NON_CONFIRMABLE, true))
                .usingGetClass()
                .verify();
    }
}
