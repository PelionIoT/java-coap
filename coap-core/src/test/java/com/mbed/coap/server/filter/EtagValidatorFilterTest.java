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
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class EtagValidatorFilterTest {

    private final Filter.SimpleFilter<CoapRequest, CoapResponse> filter = new EtagValidatorFilter();
    private final CoapResponse resource = ok("OK").etag(ofBytes(100)).maxAge(100);
    private final Service<CoapRequest, CoapResponse> service = filter.then(__ -> completedFuture(resource));

    @Test
    void shouldPassResponseWhenMissingEtagInRequest() {
        CompletableFuture<CoapResponse> resp = service.apply(get("/9"));

        assertEquals(resource, resp.join());
    }

    @Test
    void shouldReplyValidWhenRequestWithSameEtag() {
        CompletableFuture<CoapResponse> resp = service.apply(get("/9").etag(ofBytes(100)));

        assertEquals(of(Code.C203_VALID).etag(ofBytes(100)).maxAge(100), resp.join());
    }

    @Test
    void shouldReplyValidWhenRequestWithSameOneOfEtags() {
        CompletableFuture<CoapResponse> resp = service.apply(
                get("/9").options(o -> o.setEtag(new Opaque[]{ofBytes(200), ofBytes(100)}))
        );

        assertEquals(of(Code.C203_VALID).etag(ofBytes(100)).maxAge(100), resp.join());
    }

    @Test
    void shouldPassResponseWhenMethodOtherThanGET() {
        CompletableFuture<CoapResponse> resp = service.apply(post("/9").etag(ofBytes(100)));

        assertEquals(resource, resp.join());
    }
}