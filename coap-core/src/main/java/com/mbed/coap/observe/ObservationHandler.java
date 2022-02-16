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
package com.mbed.coap.observe;

import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapExchange;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationHandler.class.getName());
    private final Map<Opaque, ObservationListenerContainer> observationMap = new HashMap<>();

    public void terminate(CoapPacket terminatingPacket) {
        ObservationListenerContainer obsListContainer = observationMap.get(terminatingPacket.getToken());
        if (obsListContainer != null) {
            obsListContainer.complete(terminatingPacket.toCoapResponse());
        }
    }

    public void notify(CoapExchange t) {
        Opaque token = t.getRequest().getToken();
        final ObservationListenerContainer obsListContainer = observationMap.remove(token);
        if (obsListContainer != null) {
            BlockOption requestBlock2Res = t.getRequest().headers().getBlock2Res();
            if (requestBlock2Res != null && requestBlock2Res.getNr() == 0 && requestBlock2Res.hasMore()) {
                if (requestBlock2Res.hasMore() && requestBlock2Res.getSize() != t.getRequestBody().size()) {
                    LOGGER.warn("Block size does not match payload size " + requestBlock2Res.getSize() + "!=" + t.getRequestBody().size());
                    t.setResponse(resetResponse(t));
                    t.sendResponse();
                    return;
                }
                t.sendResponse();

                t.retrieveNotificationBlocks(obsListContainer.uriPath)
                        .thenAccept(obsListContainer::complete)
                        .exceptionally(log(LOGGER));
            } else {
                obsListContainer.complete(t.getRequest().toCoapResponse());
                if (observationMap.containsKey(token)) {
                    t.sendResponse();
                } else {
                    // observer did not provide next promise, terminating observation
                    t.sendResetResponse();
                }
            }
        } else {
            LOGGER.info("No observer for token: {}, sending reset", t.getRequest().getToken().toHex());
            t.sendResetResponse();
        }
    }

    private static CoapPacket resetResponse(CoapExchange t) {
        CoapPacket resetResponse = new CoapPacket(t.getRemoteAddress());
        resetResponse.setMessageType(MessageType.Reset);
        resetResponse.setMessageId(t.getRequest().getMessageId());
        return resetResponse;
    }

    public Supplier<CompletableFuture<CoapResponse>> nextSupplier(Opaque token, String uriPath) {
        return () -> {
            ObservationListenerContainer obsRelation = new ObservationListenerContainer(uriPath);
            ObservationListenerContainer prev = observationMap.put(token, obsRelation);
            if (prev != null) {
                prev.cancel();
            }
            return obsRelation.promise;
        };
    }

    public boolean hasObservation(Opaque token) {
        return observationMap.containsKey(token);
    }

    private static class ObservationListenerContainer {
        private final String uriPath;
        private final CompletableFuture<CoapResponse> promise;

        ObservationListenerContainer(String uriPath) {
            this.uriPath = uriPath;
            this.promise = new CompletableFuture<>();
        }

        public void cancel() {
            promise.cancel(false);
        }

        public void complete(CoapResponse obsPacket) {
            promise.complete(obsPacket);
        }
    }
}
