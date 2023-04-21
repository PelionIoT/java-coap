/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapRequestId;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.PutOnlyMap;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateDetector implements Filter.SimpleFilter<CoapPacket, CoapPacket> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateDetector.class);
    private static final CoapPacket EMPTY_COAP_PACKET = new CoapPacket(null);
    private static final CoapPacket NULL_COAP_PACKET = new CoapPacket(null);

    private final PutOnlyMap<CoapRequestId, CoapPacket> requestMap;
    private final DuplicatedCoapMessageCallback duplicatedCoapMessageCallback;

    public DuplicateDetector(PutOnlyMap<CoapRequestId, CoapPacket> cache, DuplicatedCoapMessageCallback duplicatedCoapMessageCallback) {
        this.requestMap = cache;
        this.duplicatedCoapMessageCallback = duplicatedCoapMessageCallback;
    }

    @Override
    public CompletableFuture<CoapPacket> apply(CoapPacket request, Service<CoapPacket, CoapPacket> service) {
        CoapPacket duplResp = getResponseForRepeatedRequest(request);

        if (duplResp != null) {
            duplicatedCoapMessageCallback.duplicated(request);
            if (duplResp == DuplicateDetector.EMPTY_COAP_PACKET) { // NOPMD
                LOGGER.debug("CoAP request repeated, no response available [{}]", request);
                return failedFuture(new CancellationException());
            } else if (duplResp == DuplicateDetector.NULL_COAP_PACKET) { // NOPMD
                LOGGER.debug("CoAP request repeated, null response available [{}]", request);
                return completedFuture(null);
            } else {
                LOGGER.debug("CoAP request repeated, resending response [{}]", request);
                return completedFuture(duplResp);
            }
        }

        return service.apply(request).thenApply(response -> {
            if (response != null) {
                putResponse(request, response);
            } else {
                putResponse(request, NULL_COAP_PACKET);
            }
            return response;
        });
    }

    private CoapPacket getResponseForRepeatedRequest(CoapPacket request) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress());

        return requestMap.putIfAbsent(requestId, EMPTY_COAP_PACKET);
    }

    private void putResponse(CoapPacket request, CoapPacket response) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress());
        requestMap.put(requestId, response);
    }

}
