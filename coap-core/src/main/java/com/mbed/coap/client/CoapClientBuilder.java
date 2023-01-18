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
package com.mbed.coap.client;

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.utils.Filter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

public class CoapClientBuilder<T extends CoapServerBuilder<?>> {

    protected InetSocketAddress destination;
    protected final T coapServerBuilder;
    protected boolean isTransportDefined;

    private CoapClientBuilder(T builder) {
        this.coapServerBuilder = builder;
    }

    private CoapClientBuilder(T builder, int localPort) {
        this(builder);
        setTarget(localPort);
    }

    CoapClientBuilder(T builder, InetSocketAddress destination) {
        this(builder);
        setTarget(destination);
    }


    /**
     * Creates CoAP client builder with target on localhost.
     *
     * @param localPort local port number
     * @return CoAP client builder instance
     */
    public static CoapClientBuilderForUdp newBuilder(int localPort) {
        return new CoapClientBuilderForUdp(CoapServerBuilder.newBuilder(), localPort);
    }

    /**
     * Creates CoAP client builder with target socket address.
     *
     * @param destination target address
     * @return CoAP client builder instance
     */
    public static CoapClientBuilderForUdp newBuilder(InetSocketAddress destination) {
        return new CoapClientBuilderForUdp(CoapServerBuilder.newBuilder(), destination);
    }


    public static CoapClient clientFor(InetSocketAddress target, CoapServer server) {
        return new CoapClient(target, server.clientService(), server::stop);
    }

    public CoapClient build() throws IOException {
        CoapServer server = coapServerBuilder.build();
        return new CoapClient(destination, server.start().clientService(), server::stop);
    }

    protected final void setTarget(InetSocketAddress destination) {
        this.destination = destination;
    }

    protected final void setTarget(int localPort) {
        try {
            this.destination = new InetSocketAddress(InetAddress.getLocalHost(), localPort);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static class CoapClientBuilderForUdp extends CoapClientBuilder<CoapServerBuilder.CoapServerBuilderForUdp> {
        private final CoapServerBuilder.CoapServerBuilderForUdp coapServerBuilderForUdp;

        CoapClientBuilderForUdp(CoapServerBuilder.CoapServerBuilderForUdp builder, int localPort) {
            super(builder, localPort);
            this.coapServerBuilderForUdp = builder;
        }

        CoapClientBuilderForUdp(CoapServerBuilder.CoapServerBuilderForUdp builder, InetSocketAddress destination) {
            super(builder, destination);
            this.coapServerBuilderForUdp = builder;
        }

        @Override
        public CoapClient build() throws IOException {
            if (!isTransportDefined) {
                coapServerBuilder.transport(0);
            }
            return super.build();
        }

        public CoapClientBuilderForUdp transport(CoapTransport trans) {
            coapServerBuilderForUdp.transport(trans);
            isTransportDefined = true;
            return this;
        }

        public CoapClientBuilderForUdp timeout(long singleTimeoutMili) {
            coapServerBuilderForUdp.timeout(new SingleTimeout(singleTimeoutMili));
            return this;
        }

        public CoapClientBuilderForUdp timeout(TransmissionTimeout responseTimeout) {
            coapServerBuilderForUdp.timeout(responseTimeout);
            return this;
        }

        public CoapClientBuilderForUdp finalTimeout(Duration timeout) {
            coapServerBuilderForUdp.finalTimeout(timeout);
            return this;
        }

        public CoapClientBuilderForUdp scheduledExec(ScheduledExecutorService scheduledExecutorService) {
            coapServerBuilderForUdp.scheduledExecutor(scheduledExecutorService);
            return this;
        }

        public CoapClientBuilderForUdp transport(int bindingPort) {
            coapServerBuilderForUdp.transport(bindingPort);
            isTransportDefined = true;
            return this;
        }

        public CoapClientBuilderForUdp blockSize(BlockSize blockSize) {
            coapServerBuilderForUdp.blockSize(blockSize);
            return this;
        }

        public CoapClientBuilderForUdp maxIncomingBlockTransferSize(int maxSize) {
            coapServerBuilderForUdp.maxIncomingBlockTransferSize(maxSize);
            return this;
        }

        public CoapClientBuilderForUdp outboundFilter(Filter.SimpleFilter<CoapRequest, CoapResponse> outboundFilter){
            coapServerBuilderForUdp.outboundFilter(outboundFilter);
            return this;
        }

    }

}
