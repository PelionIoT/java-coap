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

    private CoapRequest(InetSocketAddress peerAddress, TransportContext transContext) {
        // ping
        this.method = null;
        this.token = Opaque.EMPTY;
        this.options = new HeaderOptions();
        this.payload = Opaque.EMPTY;
        this.peerAddress = Objects.requireNonNull(peerAddress);
        this.transContext = Objects.requireNonNull(transContext);
    }


    // --- STATIC BUILDERS ---

    public static CoapRequest of(InetSocketAddress peerAddress, Method method, String uriPath) {
        HeaderOptions options = new HeaderOptions();
        options.setUriPath(uriPath);

        return new CoapRequest(method, Opaque.EMPTY, options, Opaque.EMPTY, peerAddress, TransportContext.EMPTY);
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

    public static CoapRequest ping(InetSocketAddress peerAddress, TransportContext transContext) {
        return new CoapRequest(peerAddress, transContext);
    }

    public static CoapRequest observe(InetSocketAddress peerAddress, String uriPath) {
        CoapRequest obsRequest = new CoapRequest(Method.GET, Opaque.EMPTY, new HeaderOptions(), Opaque.EMPTY, peerAddress, TransportContext.EMPTY);
        obsRequest.options().setObserve(0);
        obsRequest.options().setUriPath(uriPath);

        return obsRequest;
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

    public boolean isPing() {
        return method == null && token.isEmpty() && payload.isEmpty();
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
        if (method == null) {
            return "CoapRequest[PING]";
        }
        if (payload.isEmpty() && token.isEmpty()) {
            return String.format("CoapRequest[%s%s]", method.toString(), options);
        } else if (payload.isEmpty()) {
            return String.format("CoapRequest[%s%s,Token:%s]", method.toString(), options, token.toHex());
        } else if (token.isEmpty()) {
            return String.format("CoapRequest[%s%s, pl(%d):%s]", method.toString(), options, payload.size(), payload.toHexShort(20));
        }
        return String.format("CoapRequest[%s%s,Token:%s, pl(%d):%s]", method.toString(), options, token.toHex(), payload.size(), payload.toHexShort(20));
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

    public CoapRequest payload(Opaque newPayload, short contentFormat) {
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

    public CoapRequest block1Req(int num, BlockSize size, boolean more) {
        options.setBlock1Req(new BlockOption(num, size, more));
        return this;
    }

    public CoapRequest block2Res(int num, BlockSize size, boolean more) {
        options.setBlock2Res(new BlockOption(num, size, more));
        return this;
    }

    public CoapRequest size1(int size) {
        options.setSize1(size);
        return this;
    }

    public CoapRequest maxAge(long maxAge) {
        this.options.setMaxAge(maxAge);
        return this;
    }

    public CoapRequest host(String host) {
        this.options.setUriHost(host);
        return this;
    }

    public CoapRequest query(String name, String val) {
        if (name.isEmpty() || name.contains("=") || name.contains("&") || name.contains("?")
                || val.isEmpty() || val.contains("=") || val.contains("&") || val.contains("?")) {
            throw new IllegalArgumentException("Non valid characters provided in query");
        }
        final StringBuilder query = new StringBuilder();
        if (options.getUriQuery() != null) {
            query.append(options.getUriQuery());
            query.append('&');
        }
        query.append(name).append('=').append(val);
        options.setUriQuery(query.toString());
        return this;
    }

    public CoapRequest blockSize(BlockSize size) {
        if (method == Method.GET) {
            options.setBlock2Res(new BlockOption(0, size, false));
        }
        if (method == Method.PUT || method == Method.POST) {
            options.setBlock1Req(new BlockOption(0, size, true));
        }
        return this;
    }

    public CoapRequest query(String uriQuery) {
        options.setUriQuery(uriQuery);
        return this;
    }

    public CoapRequest proxy(String proxyUri) {
        options.setProxyUri(proxyUri);
        return this;
    }

    public CoapRequest ifMatch(Opaque etag) {
        options.setIfMatch(new Opaque[]{etag});
        return this;
    }

    public CoapRequest accept(short contentFormat) {
        options.setAccept(contentFormat);
        return this;
    }
}
