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
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Consumer;

public final class CoapRequest {
    private final Method method;
    private final Opaque token;
    private final HeaderOptions options;
    private final Opaque payload;
    private final InetSocketAddress peerAddress;
    private final TransportContext transContext;

    public CoapRequest(Method method, Opaque token, HeaderOptions options, Opaque payload, InetSocketAddress peerAddress, TransportContext transContext) {
        this.method = Objects.requireNonNull(method);
        this.token = Objects.requireNonNull(token);
        this.options = Objects.requireNonNull(options);
        this.payload = Objects.requireNonNull(payload);
        this.peerAddress = peerAddress;
        this.transContext = Objects.requireNonNull(transContext);
    }

    // --- STATIC BUILDERS ---

    public static CoapRequest of(InetSocketAddress peerAddress, Method method, String uriPath) {
        HeaderOptions options = new HeaderOptions();
        options.setUriPath(uriPath);

        return new CoapRequest(method, Opaque.EMPTY, options, Opaque.EMPTY, peerAddress, TransportContext.NULL);
    }

    public static CoapRequest get(InetSocketAddress peerAddress, String uriPath) {
        return CoapRequest.of(peerAddress, Method.GET, uriPath);
    }

    public static CoapRequest get(String uriPath) {
        return CoapRequest.of(null, Method.GET, uriPath);
    }

    public static CoapRequest post(InetSocketAddress peerAddress, String uriPath) {
        return CoapRequest.of(peerAddress, Method.POST, uriPath);
    }

    public static CoapRequest post(String uriPath) {
        return CoapRequest.of(null, Method.POST, uriPath);
    }

    public static CoapRequest put(InetSocketAddress peerAddress, String uriPath) {
        return CoapRequest.of(peerAddress, Method.PUT, uriPath);
    }

    public static CoapRequest put(String uriPath) {
        return CoapRequest.of(null, Method.PUT, uriPath);
    }

    public static CoapRequest delete(InetSocketAddress peerAddress, String uriPath) {
        return CoapRequest.of(peerAddress, Method.DELETE, uriPath);
    }

    public static CoapRequest delete(String uriPath) {
        return CoapRequest.of(null, Method.DELETE, uriPath);
    }

    // --------------------


    public Method getMethod() {
        return method;
    }

    public Opaque getToken() {
        return token;
    }

    public HeaderOptions options() {
        return options;
    }

    public Opaque getPayload() {
        return payload;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    public TransportContext getTransContext() {
        return transContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoapRequest that = (CoapRequest) o;
        return method == that.method && Objects.equals(token, that.token) && Objects.equals(options, that.options) && Objects.equals(payload, that.payload) && Objects.equals(peerAddress, that.peerAddress) && Objects.equals(transContext, that.transContext);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(method, options, peerAddress, transContext);
        result = 31 * result + Objects.hashCode(token);
        result = 31 * result + Objects.hashCode(payload);
        return result;
    }

    public String toString() {
        return String.format("CoapRequest[%s%s,Token:%s, pl:%s]", method.toString(), options, token.toHex(), payload.toHexShort(20));
    }


    // ---  MODIFIERS ---
    public CoapRequest token(Opaque newToken) {
        return new CoapRequest(method, newToken, options, payload, peerAddress, transContext);
    }

    public CoapRequest token(long token) {
        return token(Opaque.variableUInt(token));
    }


    public CoapRequest payload(Opaque newPayload) {
        return new CoapRequest(method, token, options, newPayload, peerAddress, transContext);
    }

    public CoapRequest payload(String newPayload) {
        return payload(Opaque.of(newPayload));
    }

    public CoapRequest payload(String newPayload, short contentFormat) {
        options.setContentFormat(contentFormat);
        return payload(newPayload);
    }

    public CoapRequest address(InetSocketAddress newPeerAddress) {
        return new CoapRequest(method, token, options, payload, newPeerAddress, transContext);
    }

    public CoapRequest context(TransportContext newTransportContext) {
        return new CoapRequest(method, token, options, payload, peerAddress, newTransportContext);
    }

    // --- OPTIONS MODIFIERS ---

    public CoapRequest options(Consumer<HeaderOptions> optionsConsumer) {
        optionsConsumer.accept(options);
        return this;
    }

    public CoapRequest observe(int observe) {
        options.setObserve(observe);
        return this;
    }

    public CoapRequest etag(Opaque etag) {
        options.setEtag(etag);
        return this;
    }
}
