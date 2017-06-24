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

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * @author szymon
 */
public abstract class CoapServerAbstract implements CoapReceiver {

    protected long delayedTransactionTimeout;
    protected TransmissionTimeout transmissionTimeout;
    protected DuplicatedCoapMessageCallback duplicatedCoapMessageCallback;

    long getDelayedTransactionTimeout() {
        return delayedTransactionTimeout;
    }

    TransmissionTimeout getTransmissionTimeout() {
        return transmissionTimeout;
    }

    protected void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp) {
        sendResponseAndUpdateDuplicateDetector(request, resp, TransportContext.NULL);
    }

    private void sendResponseAndUpdateDuplicateDetector(CoapPacket request, CoapPacket resp, TransportContext ctx) {
        putToDuplicationDetector(request, resp);
        send(resp, request.getRemoteAddress(), ctx);
    }

    /**
     * Sends CoapPacket to specified destination UDP address. This call must not throw exception. Any sending failures must be returned in CompletableFuture
     *
     * @param coapPacket CoAP packet
     * @param adr destination address
     * @param tranContext transport context
     */
    protected final CompletableFuture<Boolean> send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        if (coapPacket.getMessageType() == MessageType.NonConfirmable) {
            coapPacket.setMessageId(getNextMID());
        }
        return sendPacket(coapPacket, adr, tranContext);
    }

    protected abstract CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext);

    protected abstract int getNextMID();

    protected abstract DuplicationDetector getDuplicationDetector();

    protected final void putToDuplicationDetector(CoapPacket request, CoapPacket response) {
        if (getDuplicationDetector() != null) {
            getDuplicationDetector().putResponse(request, response);
        }
    }

    protected void sendResponse(CoapExchange exchange) {
        CoapPacket resp = exchange.getResponse();
        if (resp == null) {
            //nothing to send
            return;
        }
        sendResponseAndUpdateDuplicateDetector(exchange.getRequest(), resp, exchange.getResponseTransportContext());
    }
}
