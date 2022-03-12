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
package com.mbed.coap.server;

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.FutureHelpers;
import com.mbed.coap.utils.Service;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ObservationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationHandler.class.getName());
    private final Map<Opaque, ObservationListenerContainer> observationMap = new ConcurrentHashMap<>();

    private void terminate(SeparateResponse observationResp) {
        ObservationListenerContainer obsListContainer = observationMap.remove(observationResp.getToken());
        if (obsListContainer != null) {
            obsListContainer.complete(observationResp.asResponse());
        }
    }

    public boolean notify(SeparateResponse observationResp, Service<CoapRequest, CoapResponse> clientService) {
        Integer observe = observationResp.options().getObserve();
        if (observe == null && !hasObservation(observationResp.getToken())) {
            return false;
        }
        if (observe == null || (observationResp.getCode() != Code.C205_CONTENT && observationResp.getCode() != Code.C203_VALID)) {
            LOGGER.trace("Notification termination [{}]", observationResp);
            terminate(observationResp);
            return true;
        }

        LOGGER.trace("Notification [{}]", observationResp.getPeerAddress());

        final ObservationListenerContainer obsListContainer = observationMap.remove(observationResp.getToken());

        if (obsListContainer == null) {
            LOGGER.info("No observer for token: {}, sending reset", observationResp.getToken().toHex());
            return false;
        }

        BlockOption requestBlock2Res = observationResp.options().getBlock2Res();
        if (requestBlock2Res != null && requestBlock2Res.getNr() == 0 && requestBlock2Res.hasMore()) {
            if (requestBlock2Res.getSize() != observationResp.getPayload().size()) {
                LOGGER.warn("Block size does not match payload size {}!={}", requestBlock2Res.getSize(), observationResp.getPayload().size());
                obsListContainer.cancel();
                return false;
            }
            // retrieve full notification payload
            CoapRequest fullNotifRequest = CoapRequest.get(observationResp.getPeerAddress(), obsListContainer.uriPath)
                    .block2Res(1, observationResp.options().getBlock2Res().getBlockSize(), false);

            clientService
                    .apply(fullNotifRequest)
                    .thenCompose(resp -> merge(resp, observationResp))
                    .thenAccept(obsListContainer::complete)
                    .exceptionally(FutureHelpers.log(LOGGER));
            return true;
        } else {
            obsListContainer.complete(observationResp.asResponse());
            // if observer did not provide next promise, terminating observation
            return observationMap.containsKey(observationResp.getToken());
        }
    }

    private CompletableFuture<CoapResponse> merge(CoapResponse resp, SeparateResponse observationResp) {
        if (resp.getCode() != Code.C205_CONTENT) {
            return failedFuture(new CoapCodeException(resp.getCode(), "Unexpected response when retrieving full observation message"));
        }
        if (!Objects.equals(observationResp.options().getEtag(), resp.options().getEtag())) {
            return failedFuture(new CoapException("Could not retrieve full observation message, etag does not mach"));
        }

        resp = resp.payload(observationResp.getPayload().concat(resp.getPayload()));
        return completedFuture(resp);
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

    boolean hasObservation(Opaque token) {
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
