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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public abstract class CoapServerAbstract implements CoapReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerAbstract.class.getName());
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
        try {
            putToDuplicationDetector(request, resp);
            send(resp, request.getRemoteAddress(), ctx);
        } catch (CoapException ex) {
            //problems with parsing
            LOGGER.warn(ex.getMessage());
        } catch (IOException ex) {
            //network problems
            LOGGER.error(ex.getMessage());
        }
    }

    /**
     * Sends CoapPacket to specified destination UDP address.
     *
     * @param coapPacket CoAP packet
     * @param adr destination address
     * @param tranContext transport context
     * @throws CoapException exception from CoAP layer
     * @throws IOException   exception from transport layer
     */
    protected final void send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        if (coapPacket.getMessageType() == MessageType.NonConfirmable) {
            coapPacket.setMessageId(getNextMID());
        }
        sendPacket(coapPacket, adr, tranContext);
    }

    protected abstract void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

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
