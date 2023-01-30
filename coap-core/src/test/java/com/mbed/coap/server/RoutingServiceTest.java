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

import static com.mbed.coap.packet.CoapRequest.delete;
import static com.mbed.coap.packet.CoapRequest.get;
import static com.mbed.coap.packet.CoapRequest.post;
import static com.mbed.coap.packet.CoapRequest.put;
import static com.mbed.coap.packet.MediaTypes.CT_TEXT_PLAIN;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class RoutingServiceTest {

    private Service<CoapRequest, CoapResponse> routeService = RouterService.builder()
            .get("/test/1",
                    req -> completedFuture(CoapResponse.ok("Test1", CT_TEXT_PLAIN))
            )
            .post("/test/bad-request",
                    req -> completedFuture(CoapResponse.badRequest())
            )
            .put("/path1/*",
                    req -> completedFuture(CoapResponse.of(Code.C204_CHANGED))
            )
            .delete("/test/*",
                    req -> completedFuture(CoapResponse.of(Code.C202_DELETED))
            )
            .any("/test2", req ->
                    completedFuture(CoapResponse.ok("Reply to " + req.getMethod()))
            )
            .any("/test3/*", req ->
                    completedFuture(CoapResponse.ok("Reply to " + req.getMethod()))
            )
            .build();

    @Test
    public void shouldRouteWithExactUriPath() throws ExecutionException, InterruptedException {

        // when
        CompletableFuture<CoapResponse> resp1 = routeService.apply(get("/test/1"));
        CompletableFuture<CoapResponse> resp2 = routeService.apply(post("/test/bad-request"));

        // then
        assertEquals(CoapResponse.ok("Test1", CT_TEXT_PLAIN), resp1.get());
        assertEquals(CoapResponse.badRequest(), resp2.get());
    }

    @Test
    public void shouldReturnNotFoundWhenNoRoute() throws ExecutionException, InterruptedException {

        // when
        CompletableFuture<CoapResponse> resp1 = routeService.apply(get("/test/321"));
        CompletableFuture<CoapResponse> resp2 = routeService.apply(post("/test/1"));
        CompletableFuture<CoapResponse> resp3 = routeService.apply(put("/test/1"));
        CompletableFuture<CoapResponse> resp4 = routeService.apply(delete("/no"));

        // then
        assertEquals(CoapResponse.notFound(), resp1.get());
        assertEquals(CoapResponse.notFound(), resp2.get());
        assertEquals(CoapResponse.notFound(), resp3.get());
        assertEquals(CoapResponse.notFound(), resp4.get());
    }

    @Test
    public void shouldRouteWithPrefixPath() throws ExecutionException, InterruptedException {

        // when
        CompletableFuture<CoapResponse> resp1 = routeService.apply(put("/path1/123"));
        CompletableFuture<CoapResponse> resp2 = routeService.apply(put("/path1/321123"));
        CompletableFuture<CoapResponse> resp3 = routeService.apply(delete("/test/1"));

        // then
        assertEquals(CoapResponse.of(Code.C204_CHANGED), resp1.get());
        assertEquals(CoapResponse.of(Code.C204_CHANGED), resp2.get());
        assertEquals(CoapResponse.of(Code.C202_DELETED), resp3.get());
    }

    @Test
    void shouldRouteToAnyMethod() throws ExecutionException, InterruptedException {
        // when
        CompletableFuture<CoapResponse> resp1 = routeService.apply(put("/test2"));
        CompletableFuture<CoapResponse> resp2 = routeService.apply(post("/test2"));
        CompletableFuture<CoapResponse> resp3 = routeService.apply(delete("/test2"));
        CompletableFuture<CoapResponse> resp4 = routeService.apply(get("/test3/dsds"));
        CompletableFuture<CoapResponse> resp5 = routeService.apply(delete("/test3/fsdfs"));

        // then
        assertEquals(CoapResponse.ok("Reply to PUT"), resp1.get());
        assertEquals(CoapResponse.ok("Reply to POST"), resp2.get());
        assertEquals(CoapResponse.ok("Reply to DELETE"), resp3.get());
        assertEquals(CoapResponse.ok("Reply to GET"), resp4.get());
        assertEquals(CoapResponse.ok("Reply to DELETE"), resp5.get());

    }

    @Test
    public void equalsAndHashTest() {
        EqualsVerifier.forClass(RouterService.RequestMatcher.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }

}
