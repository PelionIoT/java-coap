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

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.SocketFactory;

public abstract class CoapClientBuilder {

    protected InetSocketAddress destination;
    private final CoapServerBuilder coapServerBuilder;
    protected boolean isTransportDefined;

    CoapClientBuilder(CoapServerBuilder builder) {
        // nothing to initialize
        this.coapServerBuilder = builder;
    }

    CoapClientBuilder(CoapServerBuilder builder, int localPort) {
        this(builder);
        setTarget(localPort);
    }

    CoapClientBuilder(CoapServerBuilder builder, InetSocketAddress destination) {
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
     * Creates CoAP client builder with target on localhost.
     *
     * @param localPort local port number
     * @return CoAP client builder instance
     */
    public static CoapClientBuilderForTcp newBuilderForTcp(int localPort) {
        return new CoapClientBuilderForTcp(CoapServerBuilder.newBuilderForTcp(), localPort);
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

    /**
     * Creates CoAP client builder with target socket address.
     *
     * @param destination target address
     * @return CoAP client builder instance
     */
    public static CoapClientBuilderForTcp newBuilderForTcp(InetSocketAddress destination) {
        return new CoapClientBuilderForTcp(CoapServerBuilder.newBuilderForTcp(), destination);
    }


    public static CoapClient clientFor(InetSocketAddress target, CoapServer server) {
        return new CoapClient(target, server.clientService(), server::stop);
    }

    public CoapClient build() throws IOException {
        if (!isTransportDefined) {
            if (coapServerBuilder instanceof CoapServerBuilder.CoapServerBuilderForUdp) {
                ((CoapServerBuilder.CoapServerBuilderForUdp) coapServerBuilder).transport(0);
            } else if (coapServerBuilder instanceof CoapServerBuilder.CoapServerBuilderForTcp) {
                ((CoapServerBuilder.CoapServerBuilderForTcp) coapServerBuilder).transport(new SocketClientTransport(destination, SocketFactory.getDefault(), CoapSerializer.TCP, false));
            }
        }
        CoapServer server = coapServerBuilder.build();
        return new CoapClient(destination, server.start().clientService(), server::stop);
    }

    protected final void setTarget(InetSocketAddress destination) {
        this.destination = destination;
    }

    protected final CoapClientBuilder setTarget(int localPort) {
        try {
            this.destination = new InetSocketAddress(InetAddress.getLocalHost(), localPort);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public static class CoapClientBuilderForUdp extends CoapClientBuilder {
        private final CoapServerBuilder.CoapServerBuilderForUdp coapServerBuilderForUdp;

        CoapClientBuilderForUdp(CoapServerBuilder.CoapServerBuilderForUdp builder, int localPort) {
            super(builder, localPort);
            this.coapServerBuilderForUdp = builder;
        }

        CoapClientBuilderForUdp(CoapServerBuilder.CoapServerBuilderForUdp builder, InetSocketAddress destination) {
            super(builder, destination);
            this.coapServerBuilderForUdp = builder;
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

        public CoapClientBuilderForUdp delayedTransTimeout(int delayedTransactionTimeout) {
            coapServerBuilderForUdp.delayedTimeout(delayedTransactionTimeout);
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

    }

    public static class CoapClientBuilderForTcp extends CoapClientBuilder {
        private final CoapServerBuilder.CoapServerBuilderForTcp coapServerBuilderForTcp;

        CoapClientBuilderForTcp(CoapServerBuilder.CoapServerBuilderForTcp builder, int localPort) {
            super(builder, localPort);
            this.coapServerBuilderForTcp = builder;
        }

        CoapClientBuilderForTcp(CoapServerBuilder.CoapServerBuilderForTcp builder, InetSocketAddress destination) {
            super(builder, destination);
            this.coapServerBuilderForTcp = builder;
        }

        public CoapClientBuilderForTcp transport(CoapTransport trans) {
            coapServerBuilderForTcp.transport(trans);
            isTransportDefined = true;
            return this;
        }

        public CoapClientBuilderForTcp maxMessageSize(int maxOwnMessageSize) {
            coapServerBuilderForTcp.maxMessageSize(maxOwnMessageSize);
            return this;
        }

        public CoapClientBuilderForTcp csmStorage(CoapTcpCSMStorage csmStorage) {
            coapServerBuilderForTcp.csmStorage(csmStorage);
            return this;
        }

        @Deprecated
        public CoapClientBuilderForTcp setCsmStorage(CoapTcpCSMStorage csmStorage) {
            return csmStorage(csmStorage);
        }

        public CoapClientBuilderForTcp blockSize(BlockSize blockSize) {
            coapServerBuilderForTcp.blockSize(blockSize);
            return this;
        }

        public CoapClientBuilderForTcp maxIncomingBlockTransferSize(int maxSize) {
            coapServerBuilderForTcp.maxIncomingBlockTransferSize(maxSize);
            return this;
        }

    }
}
