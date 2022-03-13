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

import static com.mbed.coap.utils.CoapServerUtils.*;
import static java.util.Objects.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.block.BlockWiseIncomingFilter;
import com.mbed.coap.server.block.BlockWiseNotificationFilter;
import com.mbed.coap.server.block.BlockWiseOutgoingFilter;
import com.mbed.coap.server.messaging.CoapMessaging;
import com.mbed.coap.server.messaging.CoapTcpCSMStorage;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Filter.SimpleFilter;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServer.class);
    private boolean isRunning;
    private final Service<CoapRequest, CoapResponse> requestHandlingService;
    private final ObservationHandler observationHandler = new ObservationHandler();
    private final CoapMessaging coapMessaging;
    private final Service<CoapRequest, CoapResponse> clientService;

    public CoapServer(
            CoapMessaging coapMessaging,
            Filter<CoapRequest, CoapResponse, CoapRequest, CoapResponse> sendFilter,
            Service<CoapRequest, CoapResponse> routeService,
            SimpleFilter<SeparateResponse, Boolean> sendNotificationFilter
    ) {
        Service<SeparateResponse, Boolean> sendNotification = new NotificationValidator()
                .andThen(sendNotificationFilter)
                .then(coapMessaging::send);

        this.coapMessaging = coapMessaging;
        this.requestHandlingService = new RescueFilter()
                .andThen(new CriticalOptionVerifier())
                .andThen(new ObservationSenderFilter(sendNotification))
                .then(requireNonNull(routeService));

        this.clientService = new ObserveRequestFilter(observationHandler)
                .andThen(sendFilter)
                .then(coapMessaging::send);
    }

    public static CoapServer create(CoapMessaging coapMessaging, CoapTcpCSMStorage capabilities, int maxIncomingBlockTransferSize,
            Service<CoapRequest, CoapResponse> inboundFilter,
            SimpleFilter<CoapRequest, CoapResponse> outboundFilter) {
        return new CoapServer(coapMessaging,
                outboundFilter.andThen(new BlockWiseOutgoingFilter(capabilities, maxIncomingBlockTransferSize)),
                new BlockWiseIncomingFilter(capabilities, maxIncomingBlockTransferSize).then(inboundFilter),
                new BlockWiseNotificationFilter(capabilities)
        );
    }


    public static CoapServerBuilder.CoapServerBuilderForUdp builder() {
        return CoapServerBuilder.newBuilder();
    }

    /**
     * Starts CoAP server
     *
     * @return this instance
     * @throws IOException           exception from transport initialization
     * @throws IllegalStateException if server is already running
     */
    public synchronized CoapServer start() throws IOException, IllegalStateException {
        assertNotRunning();
        coapMessaging.start(obs -> observationHandler.notify(obs, clientService), this.requestHandlingService);
        isRunning = true;
        return this;
    }

    private void assertNotRunning() {
        assume(!isRunning, "CoapServer is running");
    }

    /**
     * Stops CoAP server
     *
     * @throws IllegalStateException if server is already stopped
     */
    public final synchronized void stop() throws IllegalStateException {
        if (!isRunning) {
            throw new IllegalStateException("CoapServer is not running");
        }

        isRunning = false;
        LOGGER.trace("Stopping CoAP server..");
        coapMessaging.stop();

        LOGGER.debug("CoAP Server stopped");
    }

    /**
     * Informs if server is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }


    /**
     * Returns socket address that this server is binding on
     *
     * @return socket address
     */
    public InetSocketAddress getLocalSocketAddress() {
        return coapMessaging.getLocalSocketAddress();
    }


    public final Service<CoapRequest, CoapResponse> clientService() {
        return clientService;
    }

    public void setConnectHandler(Consumer<InetSocketAddress> disconnectConsumer) {
        coapMessaging.setConnectHandler(disconnectConsumer);
    }

    public CoapMessaging getCoapMessaging() {
        return coapMessaging;
    }

}
