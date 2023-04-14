/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.transport.CoapTransport.logSent;
import static com.mbed.coap.transport.TransportContext.RESPONSE_TIMEOUT;
import static com.mbed.coap.utils.Timer.toTimer;
import static com.mbed.coap.utils.Validations.require;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.block.BlockWiseIncomingFilter;
import com.mbed.coap.server.block.BlockWiseNotificationFilter;
import com.mbed.coap.server.block.BlockWiseOutgoingFilter;
import com.mbed.coap.server.filter.CongestionControlFilter;
import com.mbed.coap.server.filter.ResponseTimeoutFilter;
import com.mbed.coap.server.messaging.Capabilities;
import com.mbed.coap.server.messaging.CapabilitiesResolver;
import com.mbed.coap.server.messaging.CoapDispatcher;
import com.mbed.coap.server.messaging.CoapRequestConverter;
import com.mbed.coap.server.messaging.DuplicateDetector;
import com.mbed.coap.server.messaging.ExchangeFilter;
import com.mbed.coap.server.messaging.MessageIdSupplier;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import com.mbed.coap.server.messaging.ObservationMapper;
import com.mbed.coap.server.messaging.PiggybackedExchangeFilter;
import com.mbed.coap.server.messaging.RetransmissionFilter;
import com.mbed.coap.transmission.RetransmissionBackOff;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import com.mbed.coap.utils.Timer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class CoapServerBuilder {
    private static final long DELAYED_TRANSACTION_TIMEOUT_MS = 120000; //2 minutes

    private CoapTransport coapTransport;
    private int duplicationMaxSize = 10000;
    private PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectionCache;
    private ScheduledExecutorService scheduledExecutorService;
    private MessageIdSupplier midSupplier = new MessageIdSupplierImpl();
    private Duration responseTimeout = Duration.ofMillis(DELAYED_TRANSACTION_TIMEOUT_MS);
    private DuplicatedCoapMessageCallback duplicatedCoapMessageCallback = DuplicatedCoapMessageCallback.NULL;
    private RetransmissionBackOff retransmissionBackOff = RetransmissionBackOff.ofDefault();
    private int maxIncomingBlockTransferSize = 10_000_000; //default to 10 MB
    private BlockSize blockSize;
    private int maxMessageSize = 1152; //default
    private Service<CoapRequest, CoapResponse> route = RouterService.NOT_FOUND_SERVICE;
    private int maxQueueSize = 100;
    private Filter.SimpleFilter<CoapRequest, CoapResponse> outboundFilter = Filter.identity();

    CoapServerBuilder() {
    }

    public CoapServerBuilder blockSize(BlockSize blockSize) {
        require(blockSize == null || !blockSize.isBert(), "BlockSize with BERT support is defined only for CoAP over TCP");

        this.blockSize = blockSize;
        return this;
    }

    public CoapServerBuilder transport(CoapTransport coapTransport) {
        this.coapTransport = requireNonNull(coapTransport);
        return this;
    }

    public CoapServerBuilder route(Service<CoapRequest, CoapResponse> route) {
        this.route = requireNonNull(route);
        return this;
    }

    public CoapServerBuilder route(RouterService.RouteBuilder routeBuilder) {
        return route(routeBuilder.build());
    }

    public CoapServerBuilder outboundFilter(Filter.SimpleFilter<CoapRequest, CoapResponse> outboundFilter) {
        this.outboundFilter = requireNonNull(outboundFilter);
        return this;
    }

    public CoapServerBuilder maxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
        return this;
    }

    public CoapServerBuilder maxIncomingBlockTransferSize(int size) {
        this.maxIncomingBlockTransferSize = size;
        return this;
    }

    private PutOnlyMap<CoapRequestId, CoapPacket> getOrCreateDuplicateDetectorCache() {
        if (duplicateDetectionCache == null) {
            duplicateDetectionCache = new DefaultDuplicateDetectorCache("Default cache", duplicationMaxSize, scheduledExecutorService);
        }
        return duplicateDetectionCache;
    }

    private CapabilitiesResolver capabilities() {
        Capabilities defaultCapability;
        if (blockSize != null) {
            defaultCapability = new Capabilities(blockSize.getSize() + 1, true);
        } else {
            defaultCapability = new Capabilities(maxMessageSize, false);
        }

        return __ -> defaultCapability;
    }

    public CoapServerBuilder executor(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        return this;
    }

    public CoapServerBuilder midSupplier(MessageIdSupplier midSupplier) {
        this.midSupplier = midSupplier;
        return this;
    }

    public CoapServerBuilder retransmission(RetransmissionBackOff retransmissionBackOff) {
        this.retransmissionBackOff = retransmissionBackOff;
        return this;
    }

    public CoapServerBuilder responseTimeout(Duration timeout) {
        require(timeout.toMillis() > 0);
        this.responseTimeout = timeout;
        return this;
    }

    public CoapServerBuilder duplicatedCoapMessageCallback(DuplicatedCoapMessageCallback duplicatedCallback) {
        this.duplicatedCoapMessageCallback = requireNonNull(duplicatedCallback);
        return this;
    }

    public CoapServerBuilder queueMaxSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    public CoapServerBuilder duplicateMsgCacheSize(int duplicationMaxSize) {
        require(duplicationMaxSize > 0);
        this.duplicationMaxSize = duplicationMaxSize;
        return this;
    }

    public CoapServerBuilder duplicateMessageDetectorCache(PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectionCache) {
        this.duplicateDetectionCache = duplicateDetectionCache;
        return this;
    }

    public CoapServerBuilder noDuplicateCheck() {
        this.duplicationMaxSize = -1;
        return this;
    }

    public CoapServer build() {
        requireNonNull(coapTransport, "Missing transport");
        final boolean stopExecutor = scheduledExecutorService == null;
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        }

        Timer timer = toTimer(scheduledExecutorService);
        Service<CoapPacket, Boolean> sender = packet -> coapTransport.sendPacket(packet)
                .whenComplete((__, throwable) -> logSent(packet, throwable));

        ObservationHandler observationHandler = new ObservationHandler();

        // OUTBOUND
        ExchangeFilter exchangeFilter = new ExchangeFilter();
        RetransmissionFilter<CoapPacket, CoapPacket> retransmissionFilter = new RetransmissionFilter<>(timer, retransmissionBackOff, CoapPacket::isConfirmable);
        PiggybackedExchangeFilter piggybackedExchangeFilter = new PiggybackedExchangeFilter();

        Service<CoapRequest, CoapResponse> outboundService = outboundFilter
                .andThen(new ObserveRequestFilter(observationHandler))
                .andThen(new CongestionControlFilter<>(maxQueueSize, CoapRequest::getPeerAddress))
                .andThen(new BlockWiseOutgoingFilter(capabilities(), maxIncomingBlockTransferSize))
                .andThen(new ResponseTimeoutFilter<>(timer, req -> req.getTransContext().getOrDefault(RESPONSE_TIMEOUT, responseTimeout)))
                .andThen(exchangeFilter)
                .andThen(Filter.of(CoapPacket::from, CoapPacket::toCoapResponse)) // convert coap packet
                .andThenMap(midSupplier::update)
                .andThen(retransmissionFilter)
                .andThen(piggybackedExchangeFilter)
                .then(sender);


        // OBSERVATION
        Service<SeparateResponse, Boolean> sendNotification = new NotificationValidator()
                .andThen(new BlockWiseNotificationFilter(capabilities()))
                .andThen(new ResponseTimeoutFilter<>(timer, req -> req.getTransContext().getOrDefault(RESPONSE_TIMEOUT, responseTimeout)))
                .andThen(Filter.of(CoapPacket::from, CoapPacket::isAck))
                .andThenMap(midSupplier::update)
                .andThen(retransmissionFilter)
                .andThen(piggybackedExchangeFilter)
                .then(sender);

        // INBOUND
        PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectorCache = getOrCreateDuplicateDetectorCache();
        DuplicateDetector duplicateDetector = new DuplicateDetector(duplicateDetectorCache, duplicatedCoapMessageCallback);
        Service<CoapPacket, CoapPacket> inboundService = duplicateDetector
                .andThen(new CoapRequestConverter(midSupplier))
                .andThen(new RescueFilter())
                .andThen(new CriticalOptionVerifier())
                .andThen(new ObservationSenderFilter(sendNotification))
                .andThen(new BlockWiseIncomingFilter(capabilities(), maxIncomingBlockTransferSize))
                .then(route);


        Service<CoapPacket, CoapPacket> inboundObservation = duplicateDetector
                .andThen(new ObservationMapper())
                .then(obs -> completedFuture(observationHandler.notify(obs, outboundService)));

        CoapDispatcher dispatcher = new CoapDispatcher(sender, inboundObservation, inboundService,
                piggybackedExchangeFilter::handleResponse, exchangeFilter::handleResponse
        );

        return new CoapServer(coapTransport, dispatcher::handle, outboundService, () -> {
            piggybackedExchangeFilter.stop();
            duplicateDetectorCache.stop();
            if (stopExecutor) {
                scheduledExecutorService.shutdown();
            }
        });

    }

    public CoapClient buildClient(InetSocketAddress target) throws IOException {
        return CoapClient.create(target, build().start());
    }

}
