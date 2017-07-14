/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of Coap server for reliable transport (draft-ietf-core-coap-tcp-tls-09)
 */
public class CoapServerForTcp extends CoapServer {

    private final ConcurrentMap<DelayedTransactionId, RequestCallback> transactions = new ConcurrentHashMap<>();

    public CoapServerForTcp(CoapTransport coapTransport) {
        this.coapTransporter = coapTransport;
    }

    @Override
    public void makeRequest(CoapPacket packet, Callback<CoapPacket> callback, TransportContext transContext) {
        RequestCallback requestCallback = wrapCallback(callback);

        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        transactions.put(transId, requestCallback);

        sendPacket(packet, packet.getRemoteAddress(), transContext)
                .whenComplete((wasSent, maybeError) -> {
                    if (maybeError == null) {
                        requestCallback.onSent();
                    } else {
                        transactions.remove(transId);
                        requestCallback.callException(((Exception) maybeError));
                    }
                });
    }


    @Override
    protected boolean handleResponse(CoapPacket packet) {
        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        RequestCallback callback = transactions.remove(transId);

        if (callback != null) {
            callback.call(packet);
            return true;
        }

        return false;
    }


    // --- not used in reliable transport ---
    @Override
    public BlockSize getBlockSize() {
        return null;
    }

    @Override
    protected void stop0() {
        //nothing to stop
    }

    @Override
    protected boolean handleDelayedResponse(CoapPacket packet) {
        return false;
    }

    @Override
    protected boolean findDuplicate(CoapPacket request, String message) {
        return false;
    }

    @Override
    protected int getNextMID() {
        return 0;
    }

}
