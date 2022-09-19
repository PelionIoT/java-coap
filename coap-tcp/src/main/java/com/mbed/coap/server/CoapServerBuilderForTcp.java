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

import static com.mbed.coap.transport.CoapTransport.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.CoapTcpPacketConverter;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.block.BlockWiseIncomingFilter;
import com.mbed.coap.server.block.BlockWiseNotificationFilter;
import com.mbed.coap.server.block.BlockWiseOutgoingFilter;
import com.mbed.coap.server.filter.CongestionControlFilter;
import com.mbed.coap.server.messaging.Capabilities;
import com.mbed.coap.server.messaging.CapabilitiesStorage;
import com.mbed.coap.server.messaging.CapabilitiesStorageImpl;
import com.mbed.coap.server.messaging.CoapTcpDispatcher;
import com.mbed.coap.server.messaging.PayloadSizeVerifier;
import com.mbed.coap.server.messaging.TcpExchangeFilter;
import com.mbed.coap.utils.Service;
import java.util.function.Function;

public class CoapServerBuilderForTcp extends CoapServerBuilder<CoapServerBuilderForTcp> {
    public static CoapServerBuilderForTcp newBuilderForTcp() {
        return CoapServerBuilderForTcp.create();
    }

    protected CapabilitiesStorage csmStorage;

    private CoapServerBuilderForTcp() {
        csmStorage = new CapabilitiesStorageImpl();
    }

    public static CoapServerBuilderForTcp create() {
        return new CoapServerBuilderForTcp();
    }

    @Override
    protected CoapServerBuilderForTcp me() {
        return this;
    }

    @Override
    protected CapabilitiesStorage capabilities() {
        return csmStorage;
    }

    public CoapServerBuilderForTcp csmStorage(CapabilitiesStorage csmStorage) {
        this.csmStorage = csmStorage;
        return me();
    }

    public CoapServerBuilderForTcp maxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
        return this;
    }

    public CoapServerBuilderForTcp maxIncomingBlockTransferSize(int size) {
        this.maxIncomingBlockTransferSize = size;
        return this;
    }

    @Override
    public CoapServer build() {
        Service<CoapPacket, Boolean> sender = packet -> getCoapTransport()
                .sendPacket(packet)
                .whenComplete((__, throwable) -> logSent(packet, throwable));

        // NOTIFICATION
        ObservationHandler observationHandler = new ObservationHandler();
        Service<SeparateResponse, Boolean> sendNotification = new NotificationValidator()
                .andThen(new BlockWiseNotificationFilter(capabilities()))
                .andThenMap(CoapTcpPacketConverter::toCoapPacket)
                .andThen(new PayloadSizeVerifier<>(csmStorage))
                .then(sender);

        // INBOUND
        Service<CoapRequest, CoapResponse> inboundService = new RescueFilter()
                .andThenIf(hasRoute(), new CriticalOptionVerifier())
                .andThenIf(hasRoute(), new ObservationSenderFilter(sendNotification))
                .andThenIf(hasRoute(), new BlockWiseIncomingFilter(capabilities(), maxIncomingBlockTransferSize))
                .then(route);

        // OUTBOUND
        TcpExchangeFilter exchangeFilter = new TcpExchangeFilter();
        Service<CoapRequest, CoapResponse> outboundService = new ObserveRequestFilter(observationHandler)
                .andThen(new CongestionControlFilter<>(maxQueueSize, CoapRequest::getPeerAddress))
                .andThen(new BlockWiseOutgoingFilter(capabilities(), maxIncomingBlockTransferSize))
                .andThen(exchangeFilter)
                .andThenMap(CoapTcpPacketConverter::toCoapPacket)
                .then(sender);

        Function<SeparateResponse, Boolean> inboundObservation = obs -> observationHandler.notify(obs, outboundService);
        CoapTcpDispatcher dispatcher = new CoapTcpDispatcher(
                sender,
                csmStorage,
                new Capabilities(maxMessageSize, blockSize != null),
                inboundService,
                exchangeFilter::handleResponse,
                inboundObservation
        );

        return new CoapServer(coapTransport, dispatcher, outboundService);
    }

}
