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
package com.mbed.coap.server.internal;

import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Implements Block-wise transfer for CoAP server (RFC-7959)
 */
public class CoapServerBlocks extends CoapServer {

    private final CoapMessaging coapMessaging;
    private final CoapTcpCSMStorage capabilities;
    private final int maxIncomingBlockTransferSize;
    private final BlockWiseTransfer blockWiseTransfer;

    public CoapServerBlocks(CoapMessaging coapMessaging, CoapTcpCSMStorage capabilities, int maxIncomingBlockTransferSize, Service<CoapRequest, CoapResponse> route) {
        super(coapMessaging, new BlockWiseIncomingFilter(capabilities, maxIncomingBlockTransferSize).then(route));
        this.coapMessaging = coapMessaging;
        this.capabilities = capabilities;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.blockWiseTransfer = new BlockWiseTransfer(capabilities);
    }

    @Override
    public CompletableFuture<CoapPacket> makeRequest(CoapPacket request, TransportContext outgoingTransContext) {

        // make consequent requests with block priority and forces adding to queue even if it is full
        Service<CoapPacket, CoapPacket> sendService = coapPacket -> coapMessaging.makeRequest(coapPacket, outgoingTransContext);

        try {
            BlockWiseCallback blockCallback = new BlockWiseCallback(
                    sendService,
                    capabilities.getOrDefault(request.getRemoteAddress()),
                    request,
                    maxIncomingBlockTransferSize
            );

            return coapMessaging.makeRequest(request, outgoingTransContext)
                    .thenCompose(blockCallback::receive);

        } catch (CoapException e) {
            return failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<CoapPacket> sendNotification(CoapPacket notifPacket, TransportContext transContext) {
        if (useBlockTransfer(notifPacket, notifPacket.getRemoteAddress())) {
            //request that needs to use blocks
            blockWiseTransfer.updateWithFirstBlock(notifPacket);
        }
        return super.sendNotification(notifPacket, transContext);
    }

    private boolean useBlockTransfer(CoapPacket notifPacket, InetSocketAddress remoteAddress) {
        return capabilities.getOrDefault(remoteAddress).useBlockTransfer(notifPacket.getPayload());
    }

}

