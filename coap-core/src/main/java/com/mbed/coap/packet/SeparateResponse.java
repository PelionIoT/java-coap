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
package com.mbed.coap.packet;

import com.mbed.coap.transport.TransportContext;
import java.net.InetSocketAddress;
import java.util.Objects;

public class SeparateResponse {
    private final CoapResponse response;
    private final Opaque token;
    private final InetSocketAddress peerAddress;
    private final TransportContext transContext;

    public SeparateResponse(CoapResponse response, Opaque token, InetSocketAddress peerAddress, TransportContext transContext) {
        this.response = Objects.requireNonNull(response);
        this.token = Objects.requireNonNull(token);
        this.peerAddress = peerAddress;
        this.transContext = Objects.requireNonNull(transContext);
    }

    @Override
    public String toString() {
        return String.format("SeparateResponse[token:%s, %s]", token, response);
    }

    public Opaque getToken() {
        return token;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    public TransportContext getTransContext() {
        return transContext;
    }

    public Code getCode() {
        return response.getCode();
    }

    public HeaderOptions options() {
        return response.options();
    }

    public Opaque getPayload() {
        return response.getPayload();
    }

    public CoapResponse asResponse() {
        return response;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SeparateResponse that = (SeparateResponse) o;
        return Objects.equals(response, that.response) && Objects.equals(token, that.token) && Objects.equals(peerAddress, that.peerAddress) && Objects.equals(transContext, that.transContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response, token, peerAddress, transContext);
    }

    public SeparateResponse payload(Opaque newPayload) {
        return new SeparateResponse(response.payload(newPayload), token, peerAddress, transContext);
    }

    public SeparateResponse duplicate() {
        return new SeparateResponse(new CoapResponse(response.getCode(), response.getPayload(), response.options().duplicate()), token, peerAddress, transContext);
    }
}
