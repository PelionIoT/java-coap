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

import static com.mbed.coap.transport.CoapTransport.*;
import static com.mbed.coap.utils.FutureHelpers.*;
import static java.util.Objects.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.utils.Service;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoapDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapDispatcher.class);
    private final Service<CoapPacket, Boolean> sender;

    private final Service<CoapPacket, CoapPacket> observationHandler;
    private final Service<CoapPacket, CoapPacket> inboundService;
    private final Function<CoapPacket, Boolean> handleResponse;
    private final Function<SeparateResponse, Boolean> handleSeparateResponse;

    public CoapDispatcher(Service<CoapPacket, Boolean> sender,
            Service<CoapPacket, CoapPacket> observationHandler, Service<CoapPacket, CoapPacket> inboundService,
            Function<CoapPacket, Boolean> handleResponse, Function<SeparateResponse, Boolean> handleSeparateResponse) {

        this.sender = sender;
        this.observationHandler = requireNonNull(observationHandler);
        this.inboundService = requireNonNull(inboundService);
        this.handleResponse = requireNonNull(handleResponse);
        this.handleSeparateResponse = requireNonNull(handleSeparateResponse);
    }

    public void handle(CoapPacket packet) {
        logReceived(packet);
        if (handlePing(packet)) {
            return;
        }

        if (packet.getMethod() != null && packet.getMessageType() != MessageType.Acknowledgement) {
            handleRequest(packet);
            return;
        } else {
            if (handleResponse.apply(packet)) {
                return;
            } else if (packet.isSeparateResponse() && handleSeparateResponse(packet)) {
                return;
            } else if (packet.isSeparateResponse() && packet.headers().getObserve() != null) {
                handleObservation(packet);
                return;
            }
        }
        //cannot process
        handleNotProcessedMessage(packet);
    }

    private boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null && packet.getMessageType() == MessageType.Confirmable) {
            LOGGER.debug("CoAP ping received.");
            CoapPacket resp = packet.createResponse(null);
            resp.setMessageType(MessageType.Reset);
            sender.apply(resp);
            return true;
        }
        return false;
    }

    private boolean handleSeparateResponse(CoapPacket packet) {
        if (handleSeparateResponse.apply(packet.toSeparateResponse())) {
            if (packet.getMustAcknowledge()) {
                sender.apply(packet.createResponse());
            }
            return true;
        }
        return false;
    }


    private void handleRequest(CoapPacket packet) {
        inboundService.apply(packet)
                .thenAccept(sender::apply)
                .exceptionally(logError(LOGGER));
    }

    private void handleObservation(CoapPacket obsPacket) {
        observationHandler.apply(obsPacket)
                .thenAccept(sender::apply)
                .exceptionally(logError(LOGGER));
    }

    private void handleNotProcessedMessage(CoapPacket packet) {
        switch (packet.getMessageType()) {
            case Confirmable:
            case NonConfirmable:
                CoapPacket resp = new CoapPacket(packet.getRemoteAddress());
                resp.setMessageType(MessageType.Reset);
                resp.setMessageId(packet.getMessageId());
                sender.apply(resp);
                break;
            case Acknowledgement:
                LOGGER.debug("Discarding extra ACK: {}", packet);
                break;
            default:
                LOGGER.warn("Can not process CoAP message [{}]", packet);
                break;
        }
    }
}
