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
package com.mbed.coap.client;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.TransportContext;
import java.util.concurrent.CompletableFuture;

/**
 * CoAP request builder.
 */
public class CoapRequestTarget {

    private final CoapPacket requestPacket;
    private BlockSize blockSize;
    private final CoapClient coapClient;
    private TransportContext transContext = TransportContext.NULL;

    CoapRequestTarget(String path, final CoapClient coapClient) {
        this.coapClient = coapClient;
        this.requestPacket = new CoapPacket(Method.GET, MessageType.Confirmable, path, coapClient.getDestination());
    }

    /**
     * Provide byte array payload
     *
     * @param payload payload
     * @param contentFormat content-format
     * @return this instance
     */
    public CoapRequestTarget payload(Opaque payload, short contentFormat) {
        return payload(payload, (int) contentFormat);
    }

    public CoapRequestTarget payload(Opaque payload, int contentFormat) {
        requestPacket.setPayload(payload);
        requestPacket.headers().setContentFormat((short) contentFormat);
        return this;
    }

    public CoapRequestTarget payload(String payload, int contentFormat) {
        requestPacket.setPayload(payload);
        requestPacket.headers().setContentFormat((short) contentFormat);
        return this;
    }

    public CoapRequestTarget payload(String payload) {
        requestPacket.setPayload(payload);
        return this;
    }

    public CoapRequestTarget payload(Opaque payload) {
        requestPacket.setPayload(payload);
        return this;
    }

    public CoapRequestTarget token(Opaque token) {
        requestPacket.setToken(token);
        return this;
    }

    public CoapRequestTarget contentFormat(int contentFormat) {
        requestPacket.headers().setContentFormat(((short) contentFormat));
        return this;
    }

    public CoapRequestTarget token(long token) {
        requestPacket.setToken(Opaque.variableUInt(token));
        return this;
    }

    public CoapRequestTarget query(String name, String val) {
        if (name.isEmpty() || name.contains("=") || name.contains("&") || name.contains("?")
                || val.isEmpty() || val.contains("=") || val.contains("&") || val.contains("?")) {
            throw new IllegalArgumentException("Non valid characters provided in query");
        }
        final StringBuilder query = new StringBuilder();
        if (requestPacket.headers().getUriQuery() != null) {
            query.append(requestPacket.headers().getUriQuery());
            query.append('&');
        }
        query.append(name).append('=').append(val);
        requestPacket.headers().setUriQuery(query.toString());
        return this;
    }

    public CoapRequestTarget query(String uriQuery) {
        requestPacket.headers().setUriQuery(uriQuery);
        return this;
    }

    public CoapRequestTarget accept(short contentFormat) {
        requestPacket.headers().setAccept(((int) contentFormat));
        return this;
    }

    public CoapRequestTarget etag(Opaque etag) {
        requestPacket.headers().setEtag(etag);
        return this;
    }

    public CoapRequestTarget ifMatch(Opaque etag) {
        requestPacket.headers().setIfMatch(new Opaque[]{etag});
        return this;
    }

    public CoapRequestTarget ifNotMatch() {
        return ifNotMatch(true);
    }

    public CoapRequestTarget blockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
        return this;
    }

    public CoapRequestTarget ifNotMatch(boolean ifNotMatchVal) {
        requestPacket.headers().setIfNonMatch(ifNotMatchVal);
        return this;
    }

    public CoapRequestTarget host(String testhost) {
        requestPacket.headers().setUriHost(testhost);
        return this;
    }

    public CoapRequestTarget maxAge(long maxAge) {
        requestPacket.headers().setMaxAge(maxAge);
        return this;
    }

    public CoapRequestTarget header(int num, Opaque data) {
        requestPacket.headers().put(num, data);
        return this;
    }

    /**
     * Sets transport context that will be passed to transport connector.
     *
     * @param context transport context
     * @return this instance
     */
    public CoapRequestTarget context(TransportContext context) {
        this.transContext = context;
        return this;
    }

    /**
     * Marks request as non-confirmable.
     *
     * @return this instance
     */
    public CoapRequestTarget non() {
        requestPacket.setMessageType(MessageType.NonConfirmable);
        return this;
    }

    /**
     * Marks request as confirmable (default).
     *
     * @return this instance
     */
    public CoapRequestTarget con() {
        requestPacket.setMessageType(MessageType.Confirmable);
        return this;
    }

    public CoapRequestTarget proxy(String proxyUri) {
        requestPacket.headers().setProxyUri(proxyUri);
        return this;
    }

    public CompletableFuture<CoapPacket> get() {
        updatePacketWithBlock2();
        return request();
    }

    public CompletableFuture<CoapPacket> post() {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.POST);
        return request();
    }

    public CompletableFuture<CoapPacket> delete() {
        requestPacket.setMethod(Method.DELETE);
        return request();
    }

    public CompletableFuture<CoapPacket> put() {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.PUT);
        return request();
    }

    public CompletableFuture<CoapPacket> observe(ObservationListener observationListener) throws CoapException {
        requestPacket.headers().setObserve(0);
        CompletableFuture<CoapPacket> observe = coapClient.coapServer.observe(requestPacket, transContext);

        observe.thenAccept(resp ->
                coapClient.putObservationListener(observationListener, resp.getToken(), requestPacket.headers().getUriPath())
        );

        return observe;
    }

    CoapPacket getRequestPacket() {
        return requestPacket;
    }

    private CompletableFuture<CoapPacket> request() {
        return coapClient.coapServer.makeRequest(requestPacket, transContext);
    }

    public SyncRequestTarget sync() {
        return new SyncRequestTarget(this);
    }

    private void updatePacketWithBlock1() {
        if (blockSize != null) {
            requestPacket.headers().setBlock1Req(new BlockOption(0, blockSize, true));
        }
    }

    private void updatePacketWithBlock2() {
        if (blockSize != null) {
            requestPacket.headers().setBlock2Res(new BlockOption(0, blockSize, false));
        }
    }

}
