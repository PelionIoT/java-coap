/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CompletableFuture;

public class PayloadSizeVerifier<T> implements Filter.SimpleFilter<CoapPacket, T> {
    private final CapabilitiesResolver capabilitiesResolver;

    public PayloadSizeVerifier(CapabilitiesResolver capabilitiesResolver) {
        this.capabilitiesResolver = capabilitiesResolver;
    }

    @Override
    public CompletableFuture<T> apply(CoapPacket packet, Service<CoapPacket, T> service) {
        if (verifyPayloadSize(packet)) {
            return failedFuture(new CoapException("Request payload size is too big and no block transfer support is enabled for " + packet.getRemoteAddress() + ": " + packet.getPayload().size()));
        }
        return service.apply(packet);
    }

    private boolean verifyPayloadSize(CoapPacket packet) {
        int payloadLen = packet.getPayload().size();
        int maxMessageSize = capabilitiesResolver.getOrDefault(packet.getRemoteAddress()).getMaxMessageSizeInt();

        return payloadLen > maxMessageSize;
    }

}
