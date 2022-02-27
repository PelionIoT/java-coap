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
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;

public class BlockWiseOutgoingFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private final CoapTcpCSMStorage capabilities;
    private final int maxIncomingBlockTransferSize;

    BlockWiseOutgoingFilter(CoapTcpCSMStorage capabilities, int maxIncomingBlockTransferSize) {
        this.capabilities = capabilities;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
    }


    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {

        try {
            BlockWiseCallback blockCallback = new BlockWiseCallback(
                    service,
                    capabilities.getOrDefault(request.getPeerAddress()),
                    request,
                    maxIncomingBlockTransferSize
            );

            return service.apply(blockCallback.request)
                    .thenCompose(blockCallback::receive);

        } catch (CoapException e) {
            return failedFuture(e);
        }
    }
}
