/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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

import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import java8.util.function.Supplier;

/**
 * @author szymon
 */
public class ReadOnlyCoapResource extends CoapResource {

    private Supplier<String> payloadSupplier;
    private final Integer maxAgeSeconds;
    private final Short contentType;

    public void setResourceBody(String resourceBody) {
        this.payloadSupplier = () -> resourceBody;
    }

    public ReadOnlyCoapResource(String body) {
        this(() -> body);
    }

    public ReadOnlyCoapResource(Supplier<String> body) {
        this(body, null, null, -1);
    }

    public ReadOnlyCoapResource(String body, String resourceType, int maxAgeSeconds) {
        this(body, resourceType, null, maxAgeSeconds);
    }

    public ReadOnlyCoapResource(String body, String resourceType, Short contentType, int maxAgeSeconds) {
        this(() -> body, resourceType, contentType, maxAgeSeconds);
    }

    public ReadOnlyCoapResource(Supplier<String> body, String resourceType, Short contentType, int maxAgeSeconds) {
        this.payloadSupplier = body;
        if (maxAgeSeconds >= 0) {
            this.maxAgeSeconds = maxAgeSeconds;
        } else {
            this.maxAgeSeconds = null;
        }
        this.getLink().setContentType(contentType);
        if (resourceType != null) {
            this.getLink().setResourceType(resourceType);
        }
        this.contentType = contentType;
    }

    @Override
    public void get(CoapExchange ex) {
        ex.setResponseBody(payloadSupplier.get());
        ex.setResponseCode(Code.C205_CONTENT);
        if (contentType != null) {
            ex.setResponseContentType(contentType);
        }
        if (maxAgeSeconds != null) {
            ex.getResponseHeaders().setMaxAge((long) maxAgeSeconds);
        }
        ex.sendResponse();
    }
}
