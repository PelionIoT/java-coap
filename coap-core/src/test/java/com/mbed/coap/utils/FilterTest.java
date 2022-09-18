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
package com.mbed.coap.utils;

import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.utils.Filter.UnaryFilter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class FilterTest {

    private final Service<String, String> srv = request -> completedFuture("S:" + request);
    private final UnaryFilter<String> filter = (request, service) -> service
            .apply(request)
            .thenApply(resp -> "F(" + resp + ")");

    private final Filter<Integer, String, Integer, String> sumFilter = (request, service) -> service.apply(request + 1);
    private final Filter<Integer, String, Integer, String> multiplyFilter = (request, service) -> service.apply(request * 2);

    private final Service<Integer, String> numToStringSrv = request -> completedFuture(request.toString());

    @Test
    public void testService() throws ExecutionException, InterruptedException {

        String resp = srv.apply("aa").get();

        assertEquals("S:aa", resp);
    }

    @Test
    public void testFilter() throws ExecutionException, InterruptedException {
        Function<String, CompletableFuture<String>> serviceWithFilter = filter.then(srv);

        String resp = serviceWithFilter.apply("aa").get();

        assertEquals("F(S:aa)", resp);
    }

    @Test
    public void testMultiFilter() throws ExecutionException, InterruptedException {
        Function<String, CompletableFuture<String>> serviceWithFilter = filter
                .andThen(filter)
                .then(srv);

        String resp = serviceWithFilter.apply("aa").get();

        assertEquals("F(F(S:aa))", resp);
    }

    @Test
    public void testMultiFilterWithTypes() throws ExecutionException, InterruptedException {
        Function<Integer, CompletableFuture<String>> serviceWithFilter = multiplyFilter
                .andThen(sumFilter)
                .then(numToStringSrv);

        String resp = serviceWithFilter.apply(100).get();

        assertEquals("201", resp);
    }

    @Test
    public void name() throws ExecutionException, InterruptedException {
        Filter<String, String, String, Integer> f = (request, service) -> service
                .apply(request)
                .thenCompose(integer -> completedFuture(Integer.toString(integer + 1)));

        Function<String, CompletableFuture<String>> srv = f.then(s -> completedFuture(Integer.parseInt(s)));

        assertEquals("3", srv.apply("2").get());
    }

    @Test
    void identityFilter() {
        Filter.SimpleFilter<String, String> identity = Filter.identity();

        assertEquals(srv, identity.then(srv));
        assertEquals(filter, identity.andThen(filter));
    }

    @Test
    void andThenApply() {
        Service<String, Integer> service = Filter.<String, Integer>identity()
                .andThenMap(Integer::parseInt)
                .then(CompletableFuture::completedFuture);

        assertEquals(123, service.apply("123").join());
    }
}
