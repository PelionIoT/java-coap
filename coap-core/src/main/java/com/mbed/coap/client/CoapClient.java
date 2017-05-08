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
package com.mbed.coap.client;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java8.util.concurrent.CompletableFuture;

/**
 * CoAP client implementation.
 *
 * @author szymon
 */
public class CoapClient implements Closeable {

    private final InetSocketAddress destination;
    final CoapServer coapServer;
    private ObservationHandlerImpl observationHandler;

    public CoapClient(InetSocketAddress destination, CoapServer coapServer) {
        this.destination = destination;
        this.coapServer = coapServer;
        if (!coapServer.isRunning()) {
            throw new IllegalStateException("Coap server not running");
        }
    }

    /**
     * Creates request builder for provided uri-path.
     *
     * @param path uri path
     * @return request builder
     */
    public CoapRequestTarget resource(String path) {
        if (!coapServer.isRunning()) {
            throw new IllegalStateException("CoAP server not running");
        }
        if (path.contains("?") || path.contains("&")) {
            throw new IllegalArgumentException("Not supported character in path");
        }
        return new CoapRequestTarget(path, this);
    }

    public CompletableFuture<CoapPacket> ping() throws CoapException {
        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        coapServer.ping(destination, callback);
        return callback;
    }

    public void ping(Callback<CoapPacket> callback) throws CoapException {
        coapServer.ping(destination, callback);
    }

    /**
     * Close CoAP client connection.
     */
    @Override
    public void close() {
        coapServer.stop();
    }

    InetSocketAddress getDestination() {
        return destination;
    }

    void putObservationListener(ObservationListener observationListener, byte[] token, String uriPath) {
        if (observationHandler == null) {
            observationHandler = new ObservationHandlerImpl();
            coapServer.setObservationHandler(observationHandler);
        }
        observationHandler.putObservationListener(observationListener, token, uriPath);
    }

}
