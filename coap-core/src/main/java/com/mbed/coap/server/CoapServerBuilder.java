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
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.CoapTcpPacketConverter;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.block.BlockWiseIncomingFilter;
import com.mbed.coap.server.block.BlockWiseNotificationFilter;
import com.mbed.coap.server.block.BlockWiseOutgoingFilter;
import com.mbed.coap.server.filter.CongestionControlFilter;
import com.mbed.coap.server.messaging.CoapMessaging;
import com.mbed.coap.server.messaging.CoapRequestId;
import com.mbed.coap.server.messaging.CoapTcpCSM;
import com.mbed.coap.server.messaging.CoapTcpCSMStorage;
import com.mbed.coap.server.messaging.CoapTcpCSMStorageImpl;
import com.mbed.coap.server.messaging.CoapTcpDispatcher;
import com.mbed.coap.server.messaging.CoapUdpMessaging;
import com.mbed.coap.server.messaging.DefaultDuplicateDetectorCache;
import com.mbed.coap.server.messaging.MessageIdSupplier;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import com.mbed.coap.server.messaging.PayloadSizeVerifier;
import com.mbed.coap.server.messaging.TcpExchangeFilter;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;


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
    protected CoapTcpCSMStorage csmStorage;
    protected Service<CoapRequest, CoapResponse> route = RouterService.NOT_FOUND_SERVICE;
    protected int maxQueueSize = 100;

    protected abstract T me();


    public static CoapServerBuilderForUdp newBuilder() {
        return CoapServerBuilderForUdp.create();
    }

    public static CoapServerBuilderForTcp newBuilderForTcp() {
        return CoapServerBuilderForTcp.create();
    }

    public final T blockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
        return me();
    }

    public final T transport(CoapTransport coapTransport) {
        this.coapTransport = coapTransport;
        return me();
    }

    public T csmStorage(CoapTcpCSMStorage csmStorage) {
        this.csmStorage = csmStorage;
        return me();
    }

    public T route(Service<CoapRequest, CoapResponse> route) {
        this.route = route;
        return me();
    }

    public T route(RouterService.RouteBuilder routeBuilder) {
        return route(routeBuilder.build());
    }


    public CoapServer start() throws IOException {
        CoapServer coapServer = build();
        coapServer.start();
        return coapServer;
    }

    public abstract CoapServer build();

    protected abstract CoapTcpCSMStorage capabilities();

    protected CoapTransport getCoapTransport() {
        if (coapTransport == null) {
            throw new IllegalArgumentException("Transport is missing");
        }

        return coapTransport;
    }

    public static class CoapServerBuilderForUdp extends CoapServerBuilder<CoapServerBuilderForUdp> {
        private int duplicationMaxSize = DEFAULT_MAX_DUPLICATION_LIST_SIZE;
        private long duplicateMsgCleanIntervalMillis = DEFAULT_DUPLICATE_DETECTOR_CLEAN_INTERVAL_MILLIS;
        private long duplicateMsgWarningMessageIntervalMillis = DEFAULT_DUPLICATE_DETECTOR_WARNING_INTERVAL_MILLIS;
        private long duplicateMsgDetectionTimeMillis = DEFAULT_DUPLICATE_DETECTOR_DETECTION_TIME_MILLIS;
        private PutOnlyMap<CoapRequestId, CoapPacket> duplicateDetectionCache;

        private ScheduledExecutorService scheduledExecutorService;
        private MessageIdSupplier midSupplier = new MessageIdSupplierImpl();

        private long delayedTransactionTimeout = DELAYED_TRANSACTION_TIMEOUT_MS;
        private DuplicatedCoapMessageCallback duplicatedCoapMessageCallback = DuplicatedCoapMessageCallback.NULL;
        private TransmissionTimeout transmissionTimeout;

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

        public CoapServerBuilderForUdp transport(InetAddress address, int port) {
            transport(new DatagramSocketTransport(new InetSocketAddress(address, port)));
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

        private CoapUdpMessaging buildCoapMessaging() {
            boolean isSelfCreatedExecutor = false;
            if (scheduledExecutorService == null) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                isSelfCreatedExecutor = true;
            }

            if (blockSize != null && blockSize.isBert()) {
                throw new IllegalArgumentException("BlockSize with BERT support is defined only for CoAP overt TCP/TLS 2017 standard draft");
            }

            CoapUdpMessaging server = new CoapUdpMessaging(getCoapTransport());

            server.setTransmissionTimeout(transmissionTimeout);

            if (duplicateDetectionCache == null) {
                duplicateDetectionCache = new DefaultDuplicateDetectorCache<>(
                        "Default cache",
                        duplicationMaxSize,
                        duplicateMsgDetectionTimeMillis,
                        duplicateMsgCleanIntervalMillis,
                        duplicateMsgWarningMessageIntervalMillis,
                        scheduledExecutorService);
            }

            server.init(duplicateDetectionCache,
                    isSelfCreatedExecutor,
                    midSupplier,
                    delayedTransactionTimeout,
                    duplicatedCoapMessageCallback,
                    scheduledExecutorService);
            return server;
        }

        @Override
        protected CoapTcpCSMStorage capabilities() {
            if (csmStorage == null) {
                if (blockSize != null) {
                    csmStorage = new CoapTcpCSMStorageImpl(new CoapTcpCSM(blockSize.getSize() + 1, true));
                } else {
                    csmStorage = new CoapTcpCSMStorageImpl(new CoapTcpCSM(maxMessageSize, false));
                }
            }

            return csmStorage;
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

        public CoapServerBuilderForUdp delayedTimeout(long delayedTransactionTimeout) {
            if (delayedTransactionTimeout <= 0) {
                throw new IllegalArgumentException();
            }
            this.delayedTransactionTimeout = delayedTransactionTimeout;
            return this;
        }

        public CoapServerBuilderForUdp duplicatedCoapMessageCallback(DuplicatedCoapMessageCallback duplicatedCallback) {
            if (duplicatedCallback == null) {
                throw new NullPointerException();
            }
            this.duplicatedCoapMessageCallback = duplicatedCallback;
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
            if (duplicationMaxSize <= 0) {
                throw new IllegalArgumentException();
            }
            this.duplicationMaxSize = duplicationMaxSize;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgCleanIntervalInMillis(long intervalInMillis) {
            if (intervalInMillis < 1000) {
                throw new IllegalArgumentException();
            }
            this.duplicateMsgCleanIntervalMillis = intervalInMillis;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgWarningMessageIntervalInMillis(long intervalInMillis) {
            if (intervalInMillis < 1000) {
                throw new IllegalArgumentException();
            }
            this.duplicateMsgWarningMessageIntervalMillis = intervalInMillis;
            return this;
        }

        public CoapServerBuilderForUdp duplicateMsgDetectionTimeInMillis(long intervalInMillis) {
            if (intervalInMillis < 1000) {
                throw new IllegalArgumentException();
            }
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
            CoapMessaging messaging = buildCoapMessaging();

            // NOTIFICATION
            ObservationHandler observationHandler = new ObservationHandler();
            Service<SeparateResponse, Boolean> sendNotification = new NotificationValidator()
                    .andThen(new BlockWiseNotificationFilter(capabilities()))
                    .then(messaging::send);

            // INBOUND
            Service<CoapRequest, CoapResponse> inboundService = new RescueFilter()
                    .andThen(new CriticalOptionVerifier())
                    .andThen(new ObservationSenderFilter(sendNotification))
                    .then(new BlockWiseIncomingFilter(capabilities(), maxIncomingBlockTransferSize).then(route));

            // OUTBOUND
            Service<CoapRequest, CoapResponse> outboundService = new ObserveRequestFilter(observationHandler)
                    .andThen(new CongestionControlFilter<>(maxQueueSize, CoapRequest::getPeerAddress))
                    .andThen(new BlockWiseOutgoingFilter(capabilities(), maxIncomingBlockTransferSize))
                    .then(messaging::send);

            Function<SeparateResponse, Boolean> inboundObservation = obs -> observationHandler.notify(obs, outboundService);
            messaging.init(inboundObservation, inboundService);

            return new CoapServer(coapTransport, messaging, outboundService);
        }

    }

    public static class CoapServerBuilderForTcp extends CoapServerBuilder<CoapServerBuilderForTcp> {

        private CoapServerBuilderForTcp() {
            csmStorage = new CoapTcpCSMStorageImpl();
        }

        private static CoapServerBuilderForTcp create() {
            return new CoapServerBuilderForTcp();
        }

        @Override
        protected CoapServerBuilderForTcp me() {
            return this;
        }

        @Override
        protected CoapTcpCSMStorage capabilities() {
            return csmStorage;
        }

        public CoapServerBuilderForTcp maxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        public CoapServerBuilderForTcp maxIncomingBlockTransferSize(int size) {
            this.maxIncomingBlockTransferSize = size;
            return this;
        }


        @Deprecated
        public CoapServerBuilderForTcp setCsmStorage(CoapTcpCSMStorage csmStorage) {
            return csmStorage(csmStorage);
        }

        @Override
        public CoapServer build() {
            Service<CoapPacket, Boolean> sender = packet -> getCoapTransport()
                    .sendPacket(packet, packet.getRemoteAddress(), TransportContext.EMPTY)
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
                    .andThen(new CriticalOptionVerifier())
                    .andThen(new ObservationSenderFilter(sendNotification))
                    .then(new BlockWiseIncomingFilter(capabilities(), maxIncomingBlockTransferSize).then(route));

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
                    new CoapTcpCSM(maxMessageSize, blockSize != null),
                    inboundService,
                    exchangeFilter::handleResponse,
                    inboundObservation
            );

            return new CoapServer(coapTransport, dispatcher, outboundService);
        }

    }

}
