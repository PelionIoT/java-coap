/**
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
package com.mbed.coap.observe;

import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ObservableResourceService implements Service<CoapRequest, CoapResponse> {

    private CoapResponse current;
    private final ConcurrentMap<InetSocketAddress, CompletableFuture<CoapResponse>> promises = new ConcurrentHashMap<>();
    private final Service<InetSocketAddress, CoapResponse> supplier = adr -> {
        CompletableFuture<CoapResponse> promise = new CompletableFuture<>();
        CompletableFuture<CoapResponse> prev = promises.put(adr, promise);
        if (prev != null) {
            prev.cancel(false);
        }
        return promise;
    };

    public ObservableResourceService(CoapResponse current) {
        this.current = current;
        current.options().setObserve(0);
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest req) {
        if (req.options().getObserve() != null && req.options().getObserve() == 0) {
            final InetSocketAddress peerAddress = req.getPeerAddress();
            cancelPromise(peerAddress);

            return completedFuture(current.nextSupplier(
                    () -> supplier.apply(peerAddress))
            );
        } else if (req.options().getObserve() != null && req.options().getObserve() == 1) {
            cancelPromise(req.getPeerAddress());
        }

        return completedFuture(current.options(opts -> opts.setObserve(null)));
    }

    private void cancelPromise(InetSocketAddress adr) {
        CompletableFuture<CoapResponse> removed = promises.remove(adr);
        if (removed != null) {
            removed.cancel(false);
        }
    }

    public boolean putPayload(Opaque payload) {
        return put(current.payload(payload));
    }

    public boolean terminate(Code code) {
        Objects.requireNonNull(code);
        return put(new CoapResponse(code, Opaque.EMPTY, opts -> opts.setObserve(current.options().getObserve())));
    }

    public boolean put(CoapResponse obs) {
        obs.options().setObserve(current.options().getObserve() + 1);
        current = obs;

        boolean completed = false;
        for (InetSocketAddress adr : promises.keySet()) {
            completed = promises.remove(adr).complete(obs) || completed;
        }

        return completed;
    }

    public int observationRelations() {
        return promises.size();
    }

}
