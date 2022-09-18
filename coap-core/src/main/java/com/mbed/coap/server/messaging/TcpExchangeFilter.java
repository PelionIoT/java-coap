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

import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TcpExchangeFilter implements Filter<CoapRequest, CoapResponse, CoapRequest, Boolean> {

    private final ConcurrentMap<DelayedTransactionId, CompletableFuture<CoapResponse>> transactions = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, Boolean> service) {
        DelayedTransactionId tid = new DelayedTransactionId(request.getToken(), request.getPeerAddress());

        CompletableFuture<CoapResponse> promise = new CompletableFuture<>();
        transactions.put(tid, promise);

        CompletableFuture<Boolean> servicePromise = service.apply(request);
        servicePromise.whenComplete((resp, ex) -> {
            if (ex != null) {
                promise.completeExceptionally(ex);
            }
        });

        promise.whenComplete((__, ex) -> {
            servicePromise.cancel(false);
            transactions.remove(tid, promise);
        });

        return promise;
    }

    public boolean handleResponse(SeparateResponse resp) {
        if (resp.getCode() == Code.C705_ABORT) {
            removeTransactions(resp.getPeerAddress());
            return true;
        }

        DelayedTransactionId tid = new DelayedTransactionId(resp.getToken(), resp.getPeerAddress());
        CompletableFuture<CoapResponse> promise = transactions.remove(tid);
        if (promise != null) {
            return promise.complete(resp.asResponse());
        } else {
            return false;
        }
    }

    private void removeTransactions(InetSocketAddress remoteAddress) {
        for (DelayedTransactionId transId : transactions.keySet()) {
            if (transId.hasRemoteAddress(remoteAddress)) {

                CompletableFuture<CoapResponse> promise = transactions.remove(transId);
                if (promise != null) {
                    promise.completeExceptionally(new IOException("Socket closed"));
                }
            }
        }
    }

    public int transactions() {
        return transactions.size();
    }

}
