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
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilderForTcp;
import com.mbed.coap.server.messaging.CapabilitiesStorage;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class CoapClientBuilderForTcp extends CoapClientBuilder<CoapServerBuilderForTcp> {
    private final CoapServerBuilderForTcp coapServerBuilderForTcp;

    /**
     * Creates CoAP client builder with target socket address.
     *
     * @param destination target address
     * @return CoAP client builder instance
     */
    public static CoapClientBuilderForTcp newBuilderForTcp(InetSocketAddress destination) {
        return new CoapClientBuilderForTcp(CoapServerBuilderForTcp.newBuilderForTcp(), destination);
    }

    CoapClientBuilderForTcp(CoapServerBuilderForTcp builder, InetSocketAddress destination) {
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

    public CoapClientBuilderForTcp csmStorage(CapabilitiesStorage csmStorage) {
        coapServerBuilderForTcp.csmStorage(csmStorage);
        return this;
    }

    public CoapClientBuilderForTcp blockSize(BlockSize blockSize) {
        coapServerBuilderForTcp.blockSize(blockSize);
        return this;
    }

    public CoapClientBuilderForTcp maxIncomingBlockTransferSize(int maxSize) {
        coapServerBuilderForTcp.maxIncomingBlockTransferSize(maxSize);
        return this;
    }

    @Override
    public CoapClient build() throws IOException {
        CoapServer server = coapServerBuilder.build();

        return new CoapClient(destination, server.start().clientService(), server::stop) {
            @Override
            public CompletableFuture<Boolean> ping() {
                return clientService.apply(CoapRequest.ping(destination, TransportContext.EMPTY))
                        .thenApply(r -> r.getCode() == Code.C703_PONG);
            }
        };

    }
}
