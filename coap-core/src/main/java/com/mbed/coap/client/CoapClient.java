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

import static com.mbed.coap.observe.ObservationConsumer.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * CoAP client implementation.
 */
public class CoapClient implements Closeable {

    private final InetSocketAddress destination;
    private final Service<CoapRequest, CoapResponse> clientService;
    private final Closeable closeable;

    public CoapClient(InetSocketAddress destination, Service<CoapRequest, CoapResponse> clientService, Closeable closeable) {
        this.destination = destination;
        this.clientService = clientService;
        this.closeable = closeable;
    }

    public CompletableFuture<CoapResponse> send(CoapRequest request) {
        return clientService.apply(request.address(destination));
    }

    public CoapResponse sendSync(CoapRequest request) throws CoapException {
        return await(send(request));
    }

    private static CoapResponse await(CompletableFuture<CoapResponse> future) throws CoapException {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof CoapException) {
                throw (CoapException) ex.getCause();
            } else {
                throw new CoapException(ex.getCause());
            }
        }
    }

    public CompletableFuture<CoapResponse> observe(String uriPath, Function<CoapResponse, Boolean> consumer) {
        return observe(uriPath, Opaque.variableUInt(uriPath.hashCode()), consumer);
    }

    public CompletableFuture<CoapResponse> observe(String uriPath, Opaque token, Function<CoapResponse, Boolean> consumer) {
        CompletableFuture<CoapResponse> resp = clientService.apply(
                CoapRequest.observe(destination, uriPath).token(token)
        );

        resp.thenAccept(r -> consumeFrom(r.next, consumer));

        return resp;
    }


    public CompletableFuture<CoapResponse> ping() throws CoapException {
        return clientService.apply(CoapRequest.ping(destination, TransportContext.EMPTY));
    }

    /**
     * Close CoAP client connection.
     */
    @Override
    public void close() throws IOException {
        closeable.close();
    }

}
