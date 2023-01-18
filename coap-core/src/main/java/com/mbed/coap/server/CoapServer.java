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
package com.mbed.coap.server;

import static com.mbed.coap.utils.Validations.assume;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    private final CoapTransport transport;
    private final Consumer<CoapPacket> dispatcher;
    private final Service<CoapRequest, CoapResponse> outboundService;
    private final Runnable stopAll;

    public CoapServer(CoapTransport transport, Consumer<CoapPacket> dispatcher, Service<CoapRequest, CoapResponse> outboundService,
            Runnable stopAll) {
        this.transport = transport;
        this.dispatcher = dispatcher;
        this.outboundService = outboundService;
        this.stopAll = stopAll;
    }

    public static CoapServerBuilder.CoapServerBuilderForUdp builder() {
        return CoapServerBuilder.newBuilder();
    }

    /**
     * Starts CoAP server
     *
     * @return this instance
     * @throws IOException           exception from transport initialization
     * @throws IllegalStateException if server is already running
     */
    public synchronized CoapServer start() throws IOException, IllegalStateException {
        assume(!isRunning, "CoapServer is running");
        transport.start();
        isRunning = true;

        transport.receive().whenComplete(this::handle);
        return this;
    }

    private void handle(CoapPacket packet, Throwable error) {
        if (error != null) {
            stopWithError(error);
            return;
        }
        try {
            dispatcher.accept(packet);
        } catch (Exception ex) {
            LOGGER.error("Unexpected exception while handling packet: {}, error: {}", packet, ex, ex);
        }
        transport.receive().whenComplete(this::handle);
    }

    private synchronized void stopWithError(Throwable error) {
        if (isRunning) {
            LOGGER.error("CoapServer got error while receiving: {}", error, error);
            stop();
        }
    }

    /**
     * Stops CoAP server
     *
     * @throws IllegalStateException if server is already stopped
     */
    public final synchronized void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        LOGGER.trace("Stopping CoAP server..");
        stopAll.run();
        transport.stop();

        LOGGER.debug("CoAP Server stopped");
    }

    /**
     * Informs if server is running
     *
     * @return true if running
     */
    public synchronized boolean isRunning() {
        return isRunning;
    }


    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return transport.getLocalSocketAddress();
    }


    public final Service<CoapRequest, CoapResponse> clientService() {
        return outboundService;
    }

}
