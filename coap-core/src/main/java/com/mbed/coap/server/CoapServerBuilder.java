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
import static com.mbed.coap.utils.Timer.toTimer;
import static com.mbed.coap.utils.Validations.require;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.block.BlockWiseIncomingFilter;
import com.mbed.coap.server.block.BlockWiseNotificationFilter;
import com.mbed.coap.server.block.BlockWiseOutgoingFilter;
import com.mbed.coap.server.filter.CongestionControlFilter;
import com.mbed.coap.server.filter.TimeoutFilter;
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
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import com.mbed.coap.utils.Timer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public abstract class CoapServerBuilder<T extends CoapServerBuilder<?>> {
    private static final int DEFAULT_MAX_DUPLICATION_LIST_SIZE = 10000;
    private static final int DEFAULT_DUPLICATE_DETECTOR_CLEAN_INTERVAL_MILLIS = 10000;
    private static final int DEFAULT_DUPLICATE_DETECTOR_WARNING_INTERVAL_MILLIS = 10000;
    private static final int DEFAULT_DUPLICATE_DETECTOR_DETECTION_TIME_MILLIS = 30000;
    private static final long DELAYED_TRANSACTION_TIMEOUT_MS = 120000; //2 minutes

    protected CoapTransport coapTransport;

    protected int maxIncomingBlockTransferSize = 10_000_000; //default to 10 MB
    protected BlockSize blockSize;
    protected int maxMessageSize = 1152; //default
    protected Service<CoapRequest, CoapResponse> route = RouterService.NOT_FOUND_SERVICE;
    protected int maxQueueSize = 100;
    protected Filter.SimpleFilter<CoapRequest, CoapResponse> outboundFilter = Filter.identity();

    protected abstract T me();


    public static CoapServerBuilderForUdp newBuilder() {
        return CoapServerBuilderForUdp.create();
    }

    public final T blockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
        return me();
    }

    public T transport(CoapTransport coapTransport) {
        this.coapTransport = requireNonNull(coapTransport);
        return me();
    }

    public T route(Service<CoapRequest, CoapResponse> route) {
        this.route = route;
        return me();
    }

    public T route(RouterService.RouteBuilder routeBuilder) {
        return route(routeBuilder.build());
    }

    public T outboundFilter(Filter.SimpleFilter<CoapRequest, CoapResponse> outboundFilter) {
        this.outboundFilter = outboundFilter;
        return me();
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    protected boolean hasRoute() {
        return route != RouterService.NOT_FOUND_SERVICE;
    }

    public abstract CoapServer build();

    protected abstract CapabilitiesResolver capabilities();

    public static class CoapServerBuilderForUdp extends CoapServerBuilder<CoapServerBuilderForUdp> {
        private int duplicationMaxSize = DEFAULT_MAX_DUPLICATION_LIST_SIZE;
        private long duplicateMsgCleanIntervalMillis = DEFAULT_DUPLICATE_DETECTOR_CLEAN_INTERVAL_MILLIS;
        private long duplicateMsgWarningMessageIntervalMillis = DEFAULT_DUPLICATE_DETECTOR_WARNING_INTERVAL_MILLIS;
        private long duplicateMsgDetectionTimeMillis = DEFAULT_DUPLICATE_DETECTOR_DETECTION_TIME_MILLIS;
        private PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectionCache;

        private ScheduledExecutorService scheduledExecutorService;
        private MessageIdSupplier midSupplier = new MessageIdSupplierImpl();

        private Duration finalOutboundTimeout = Duration.ofMillis(DELAYED_TRANSACTION_TIMEOUT_MS);
        private DuplicatedCoapMessageCallback duplicatedCoapMessageCallback = DuplicatedCoapMessageCallback.NULL;
        private TransmissionTimeout transmissionTimeout = new CoapTimeout();

        private CoapServerBuilderForUdp() {
        }

        private static CoapServerBuilderForUdp create() {
            return new CoapServerBuilderForUdp();
        }

        @Override
        protected CoapServerBuilderForUdp me() {
            return this;
        }

        public CoapServerBuilderForUdp transport(int port) {
            transport(new DatagramSocketTransport(new InetSocketAddress(port)));
            return this;
        }

        public CoapServerBuilderForUdp maxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        public CoapServerBuilderForUdp maxIncomingBlockTransferSize(int size) {
            this.maxIncomingBlockTransferSize = size;
            return this;
        }

        private PutOnlyMap<CoapRequestId, CoapPacket> getDuplicateDetectorCache() {
            if (duplicateDetectionCache == null) {
                duplicateDetectionCache = new DefaultDuplicateDetectorCache(
                        "Default cache",
                        duplicationMaxSize,
                        duplicateMsgDetectionTimeMillis,
                        duplicateMsgCleanIntervalMillis,
                        duplicateMsgWarningMessageIntervalMillis,
                        scheduledExecutorService);
            }
            return duplicateDetectionCache;
        }

        @Override
        protected CapabilitiesResolver capabilities() {
            Capabilities defaultCapability;
            if (blockSize != null) {
                defaultCapability = new Capabilities(blockSize.getSize() + 1, true);
            } else {
                defaultCapability = new Capabilities(maxMessageSize, false);
            }

            return __ -> defaultCapability;
        }

        public CoapServerBuilderForUdp scheduledExecutor(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        public CoapServerBuilderForUdp midSupplier(MessageIdSupplier midSupplier) {
            this.midSupplier = midSupplier;
            return this;
        }

        public CoapServerBuilderForUdp timeout(TransmissionTimeout timeout) {
            transmissionTimeout = timeout;
            return this;
        }

        public CoapServerBuilderForUdp finalTimeout(Duration timeout) {
            require(timeout.toMillis() > 0);
            this.finalOutboundTimeout = timeout;
            return this;
        }

        public CoapServerBuilderForUdp duplicatedCoapMessageCallback(DuplicatedCoapMessageCallback duplicatedCallback) {
            this.duplicatedCoapMessageCallback = requireNonNull(duplicatedCallback);
            return this;
        }

        public CoapServerBuilderForUdp queueMaxSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Sets maximum number or requests to be kept for duplication detection.
         *
         * @param duplicationMaxSize maximum size
         * @return this instance
         */
        public CoapServerBuilderForUdp duplicateMsgCacheSize(int duplicationMaxSize) {
            require(duplicationMaxSize > 0);
            this.duplicationMaxSize = duplicationMaxSize;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgCleanIntervalInMillis(long intervalInMillis) {
            require(intervalInMillis >= 1000);
            this.duplicateMsgCleanIntervalMillis = intervalInMillis;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgWarningMessageIntervalInMillis(long intervalInMillis) {
            require(intervalInMillis >= 1000);
            this.duplicateMsgWarningMessageIntervalMillis = intervalInMillis;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgDetectionTimeInMillis(long intervalInMillis) {
            require(intervalInMillis >= 1000);
            this.duplicateMsgDetectionTimeMillis = intervalInMillis;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMessageDetectorCache(PutOnlyMap<CoapRequestId, CoapPacket> cache) {
            this.duplicateDetectionCache = cache;
            return this;
        }

        public CoapServerBuilderForUdp disableDuplicateCheck() {
            this.duplicationMaxSize = -1;
            return this;
        }

        @Override
        public CoapServer build() {
            requireNonNull(coapTransport);
            if (scheduledExecutorService == null) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            }

            if (blockSize != null && blockSize.isBert()) {
                throw new IllegalArgumentException("BlockSize with BERT support is defined only for CoAP overt TCP/TLS 2017 standard draft");
            }


            Timer timer = toTimer(scheduledExecutorService);
            Service<CoapPacket, Boolean> sender = packet -> coapTransport.sendPacket(packet)
                    .whenComplete((__, throwable) -> logSent(packet, throwable));

            ObservationHandler observationHandler = new ObservationHandler();

            // OUTBOUND
            ExchangeFilter exchangeFilter = new ExchangeFilter();
            RetransmissionFilter<CoapPacket, CoapPacket> retransmissionFilter = new RetransmissionFilter<>(timer, transmissionTimeout, CoapPacket::isConfirmable);
            PiggybackedExchangeFilter piggybackedExchangeFilter = new PiggybackedExchangeFilter();

            Service<CoapRequest, CoapResponse> outboundService = outboundFilter
                    .andThen(new ObserveRequestFilter(observationHandler))
                    .andThen(new CongestionControlFilter<>(maxQueueSize, CoapRequest::getPeerAddress))
                    .andThen(new BlockWiseOutgoingFilter(capabilities(), maxIncomingBlockTransferSize))
                    .andThen(new TimeoutFilter<>(timer, finalOutboundTimeout))
                    .andThen(exchangeFilter)
                    .andThen(Filter.of(CoapPacket::from, CoapPacket::toCoapResponse)) // convert coap packet
                    .andThenMap(midSupplier::update)
                    .andThen(retransmissionFilter)
                    .andThen(piggybackedExchangeFilter)
                    .then(sender);


            // OBSERVATION
            Service<SeparateResponse, Boolean> sendNotification = new NotificationValidator()
                    .andThen(new BlockWiseNotificationFilter(capabilities()))
                    .andThen(new TimeoutFilter<>(timer, finalOutboundTimeout))
                    .andThen(Filter.of(CoapPacket::from, CoapPacket::isAck))
                    .andThenMap(midSupplier::update)
                    .andThen(retransmissionFilter)
                    .andThen(piggybackedExchangeFilter)
                    .then(sender);

            // INBOUND
            PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectorCache = getDuplicateDetectorCache();
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
            });

        }
    }

}
