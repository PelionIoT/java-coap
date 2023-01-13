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
package com.mbed.coap.server;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.utils.FutureHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RescueFilterTest {

    private final RescueFilter filter = new RescueFilter();

    @Test
    void shouldConvertCoapCodeExceptionToResponse() {
        CompletableFuture<CoapResponse> resp = filter.apply(
                get("/"), __ -> failedFuture(new CoapCodeException(Code.C403_FORBIDDEN, "error message"))
        );

        assertEquals(CoapResponse.of(Code.C403_FORBIDDEN, "error message"), resp.join());
    }

    @Test
    void shouldConvertExceptionToResponse() {
        CompletableFuture<CoapResponse> resp = filter.apply(
                get("/"), __ -> failedFuture(new Exception("error message"))
        );

        assertEquals(CoapResponse.of(Code.C500_INTERNAL_SERVER_ERROR), resp.join());
    }

    @Test
    void shouldCatchExceptionAndConvertToResponse() {
        CompletableFuture<CoapResponse> resp = filter.apply(
                get("/"), __ -> {
                    throw new IllegalStateException("error message");
                }
        );

        assertEquals(CoapResponse.of(Code.C500_INTERNAL_SERVER_ERROR), resp.join());
    }
}
