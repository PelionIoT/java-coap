/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CoapResponse {
    private final Code code;
    private final HeaderOptions options;
    private final Opaque payload;
    public transient final Supplier<CompletableFuture<CoapResponse>> next;

    private CoapResponse(Code code, Opaque payload, HeaderOptions options, Supplier<CompletableFuture<CoapResponse>> next) {
        this.code = code;
        this.payload = Objects.requireNonNull(payload);
        this.options = Objects.requireNonNull(options);
        this.next = next;
    }

    public CoapResponse(Code code, Opaque payload, HeaderOptions options) {
        this(code, payload, options, null);
    }

    public CoapResponse(Code code, Opaque payload) {
        this(code, payload, new HeaderOptions());
    }

    public CoapResponse(Code code, Opaque payload, Consumer<HeaderOptions> optionsFunc) {
        this(code, payload);
        optionsFunc.accept(options);
    }

    // --- STATIC CONSTRUCTORS ---

    public static CoapResponse of(Code code) {
        return new CoapResponse(code, Opaque.EMPTY);
    }

    public static CoapResponse of(Code code, Opaque payload) {
        return new CoapResponse(code, payload);
    }

    public static CoapResponse of(Code code, Opaque payload, short contentFormat) {
        return new CoapResponse(code, payload, opts -> opts.setContentFormat(contentFormat));
    }

    public static CoapResponse of(Code code, String description) {
        return of(code, Opaque.of(description));
    }

    public static CoapResponse ok(Opaque payload) {
        return new CoapResponse(Code.C205_CONTENT, payload);
    }

    public static CoapResponse ok(String payload) {
        return ok(Opaque.of(payload));
    }

    public static CoapResponse ok(Opaque payload, short contentFormat) {
        return new CoapResponse(Code.C205_CONTENT, payload, opts -> opts.setContentFormat(contentFormat));
    }

    public static CoapResponse ok(String payload, short contentFormat) {
        return new CoapResponse(Code.C205_CONTENT, Opaque.of(payload), opts -> opts.setContentFormat(contentFormat));
    }

    public static CoapResponse notFound() {
        return new CoapResponse(Code.C404_NOT_FOUND, Opaque.EMPTY);
    }

    public static CoapResponse badRequest() {
        return new CoapResponse(Code.C400_BAD_REQUEST, Opaque.EMPTY);
    }

    static CoapResponse badRequest(String errorDescription) {
        return new CoapResponse(Code.C400_BAD_REQUEST, Opaque.of(errorDescription));
    }

    // ---------------------

    public Code getCode() {
        return code;
    }

    public HeaderOptions options() {
        return options;
    }

    public Opaque getPayload() {
        return payload;
    }

    public String getPayloadString() {
        return payload.toUtf8String();
    }

    public SeparateResponse toSeparate(Opaque token, InetSocketAddress peerAddress, TransportContext transContext) {
        return new SeparateResponse(this, token, peerAddress, transContext);
    }

    public SeparateResponse toSeparate(Opaque token, InetSocketAddress peerAddress) {
        return toSeparate(token, peerAddress, TransportContext.EMPTY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoapResponse that = (CoapResponse) o;
        return Objects.equals(code, that.code) && Objects.equals(options, that.options) && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(code, options);
        result = 31 * result + Objects.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        String codeString = code != null ? code.codeToString() : "na";
        String optionsString = options.toString();
        String optionsComma = optionsString.isEmpty() ? "" : ",";

        if (payload.isEmpty()) {
            return String.format("CoapResponse[%s%s%s]", codeString, optionsComma, optionsString);
        } else {
            return String.format("CoapResponse[%s%s%s, pl(%d):%s]", codeString, optionsComma, optionsString, payload.size(), payload.toHexShort(20));
        }
    }

    // ---  MODIFIERS ---

    public CoapResponse nextSupplier(Supplier<CompletableFuture<CoapResponse>> next) {
        return new CoapResponse(code, payload, options, next);
    }

    public CoapResponse payload(Opaque newPayload) {
        return new CoapResponse(code, newPayload, options);
    }

    public CoapResponse payload(String newPayload) {
        return payload(Opaque.of(newPayload));
    }

    public CoapResponse payload(String newPayload, short contentFormat) {
        return payload(Opaque.of(newPayload), contentFormat);
    }

    public CoapResponse payload(Opaque newPayload, short contentFormat) {
        options.setContentFormat(contentFormat);
        return payload(newPayload);
    }

    public CoapResponse options(Consumer<HeaderOptions> optionsFunc) {
        HeaderOptions newOpts = options.duplicate();
        optionsFunc.accept(newOpts);
        return new CoapResponse(code, payload, newOpts);
    }

    public CoapResponse etag(Opaque etag) {
        options.setEtag(etag);
        return this;
    }

    public CoapResponse maxAge(long maxAge) {
        options.setMaxAge(maxAge);
        return this;
    }

    public CoapResponse block1Req(int num, BlockSize size, boolean more) {
        options.setBlock1Req(new BlockOption(num, size, more));
        return this;
    }

    public CoapResponse block2Res(int num, BlockSize size, boolean more) {
        options.setBlock2Res(new BlockOption(num, size, more));
        return this;
    }

    public CoapResponse observe(int observe) {
        options.setObserve(observe);
        return this;
    }

}
