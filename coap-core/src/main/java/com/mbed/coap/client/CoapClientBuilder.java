/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author szymon
 */
public final class CoapClientBuilder {

    private InetSocketAddress destination;
    private final CoapServerBuilder coapServerBuilder = CoapServerBuilder.newBuilder();
    private boolean isTransportDefined;

    CoapClientBuilder() {
        // nothing to initialize
    }

    CoapClientBuilder(int localPort) {
        target(localPort);
    }

    CoapClientBuilder(InetSocketAddress destination) {
        target(destination);
    }

    /**
     * Creates CoAP client builder.
     *
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder() {
        return new CoapClientBuilder();
    }

    /**
     * Creates CoAP client builder with target on localhost.
     *
     * @param localPort local port number
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder(int localPort) {
        return new CoapClientBuilder(localPort);
    }

    /**
     * Creates CoAP client builder with target socket address.
     *
     * @param destination target address
     * @return CoAP client builder instance
     */
    public static CoapClientBuilder newBuilder(InetSocketAddress destination) {
        return new CoapClientBuilder(destination);
    }

    public static CoapClient clientFor(InetSocketAddress target, CoapServer server) {
        return new CoapClient(target, server);
    }

    public CoapClient build() throws IOException {
        if (!isTransportDefined) {
            coapServerBuilder.transport(0);
        }
        return new CoapClient(destination, coapServerBuilder.build().start());
    }

    public CoapClientBuilder target(InetSocketAddress destination) {
        this.destination = destination;
        return this;
    }

    public CoapClientBuilder target(int localPort) {
        try {
            this.destination = new InetSocketAddress(InetAddress.getLocalHost(), localPort);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public CoapClientBuilder transport(CoapTransport trans) {
        coapServerBuilder.transport(trans);
        isTransportDefined = true;
        return this;
    }

    public CoapClientBuilder transport(int bindingPort) {
        coapServerBuilder.transport(bindingPort);
        isTransportDefined = true;
        return this;
    }

    public CoapClientBuilder timeout(long singleTimeoutMili) {
        coapServerBuilder.timeout(new SingleTimeout(singleTimeoutMili));
        return this;
    }

    public CoapClientBuilder timeout(TransmissionTimeout responseTimeout) {
        coapServerBuilder.timeout(responseTimeout);
        return this;
    }

    public CoapClientBuilder blockSize(BlockSize blockSize) {
        coapServerBuilder.blockSize(blockSize);
        return this;
    }

    public CoapClientBuilder delayedTransTimeout(int delayedTransactionTimeout) {
        coapServerBuilder.delayedTimeout(delayedTransactionTimeout);
        return this;
    }

    public CoapClientBuilder maxIncomingBlockTransferSize(int maxSize) {
        coapServerBuilder.maxIncomingBlockTransferSize(maxSize);
        return this;
    }

    public CoapClientBuilder scheduledExec(ScheduledExecutorService scheduledExecutorService) {
        coapServerBuilder.scheduledExecutor(scheduledExecutorService);
        return this;
    }

    public CoapClientBuilder midSupplier(MessageIdSupplier midSupplier) {
        coapServerBuilder.midSupplier(midSupplier);
        return this;
    }

}
