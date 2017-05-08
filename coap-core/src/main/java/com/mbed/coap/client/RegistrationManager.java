/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.client;

import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.Callback;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java8.util.Optional;
import java8.util.function.Supplier;
import java8.util.stream.RefStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Duration;

public class RegistrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationManager.class);
    private final Duration minRetryDelay;
    private final Duration maxRetryDelay;
    private final URI registrationUri;
    private final CoapClient client;
    private final ScheduledExecutorService scheduledExecutor;
    private final Supplier<String> registrationLinks;
    private final String epName;
    private volatile Optional<String> registrationLocation = Optional.empty();
    private volatile Duration lastRetryDelay = Duration.ZERO;

    private final Callback<CoapPacket> registrationCallback = new Callback<CoapPacket>() {
        @Override
        public void call(CoapPacket resp) {
            if (resp.getCode().equals(Code.C201_CREATED)) {
                registrationSuccess(resp.headers().getLocationPath(), resp.headers().getMaxAgeValue());
            } else {
                registrationFailed(String.format("%s '%s'", resp.getCode().codeToString(), resp.getPayloadString() != null ? resp.getPayloadString() : ""));
            }
        }

        @Override
        public void callException(Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            registrationFailed(ex.getMessage());
        }
    };

    private final Callback<CoapPacket> updateCallback = new Callback<CoapPacket>() {
        @Override
        public void call(CoapPacket resp) {
            if (resp.getCode().equals(Code.C201_CREATED) || resp.getCode().equals(Code.C204_CHANGED)) {
                LOGGER.info("[EP:{}] Updated, lifetime: {}s", epName, resp.headers().getMaxAgeValue());
                scheduleUpdate(resp.headers().getMaxAgeValue());
            } else {
                updateFailed(resp.getCode().codeToString() + " " + (resp.getPayloadString() != null ? resp.getPayloadString() : ""));
            }
        }

        @Override
        public void callException(Exception e) {
            LOGGER.error(e.getMessage(), e);
            updateFailed(e.getMessage());
        }
    };

    public RegistrationManager(CoapServer server, URI registrationUri, ScheduledExecutorService scheduledExecutor,
            Duration minRetryDelay, Duration maxRetryDelay) {

        if (minRetryDelay.compareTo(maxRetryDelay) > 0) {
            throw new IllegalArgumentException();
        }

        this.epName = epNameFrom(registrationUri);
        this.client = new CoapClient(new InetSocketAddress(registrationUri.getHost(), registrationUri.getPort()), server);
        this.scheduledExecutor = scheduledExecutor;
        this.registrationUri = registrationUri;
        this.registrationLinks = () -> LinkFormatBuilder.toString(server.getResourceLinks());
        this.minRetryDelay = minRetryDelay;
        this.maxRetryDelay = maxRetryDelay;

        server.setConnectHandler(address -> {
            LOGGER.info("Reconnected, updating registration...");
            updateRegistration();
        });
    }

    private String epNameFrom(URI registrationUri) {
        return RefStreams.of(registrationUri.getQuery().split("&"))
                .filter(s -> s.startsWith("ep="))
                .map(s -> s.substring(3))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing 'ep' parameter"));
    }

    public RegistrationManager(CoapServer server, URI registrationUri, ScheduledExecutorService scheduledExecutor) {
        this(server, registrationUri, scheduledExecutor, Duration.ofSeconds(10), Duration.ofMinutes(5));
    }

    public void register() {
        client.resource(registrationUri.getPath())
                .query(registrationUri.getQuery())
                .payload(registrationLinks.get(), MediaTypes.CT_APPLICATION_LINK__FORMAT)
                .post(registrationCallback);
    }

    private void registrationSuccess(String locationPath, long maxAge) {
        registrationLocation = Optional.of(locationPath);
        lastRetryDelay = Duration.ZERO;
        scheduleUpdate(maxAge);
        LOGGER.info("[EP:{}] Registered, lifetime: {}s", epName, maxAge);
    }

    private void scheduleUpdate(long lifetime) {
        scheduledExecutor.schedule(this::updateRegistration, lifetime > 60 ? lifetime - 30 : lifetime - 5, TimeUnit.SECONDS);
    }

    private void updateRegistration() {
        client.resource(registrationLocation.get()).post(updateCallback);
    }

    private void updateFailed(String errMessage) {
        LOGGER.warn("[EP:{}] Update failed. {}", epName, errMessage);
        registrationLocation = Optional.empty();
        register();
    }

    protected void registrationFailed(String errMessage) {
        lastRetryDelay = nextDelay(lastRetryDelay);
        registrationLocation = Optional.empty();
        scheduledExecutor.schedule(this::register, lastRetryDelay.getSeconds(), TimeUnit.SECONDS);
        LOGGER.warn("[EP:{}] Registration failed, re-try in {}s. ({})", epName, lastRetryDelay.getSeconds(), errMessage);
    }


    public void removeRegistration() {
        registrationLocation.ifPresent(loc -> {
            client.resource(loc).delete();
            registrationLocation = Optional.empty();
        });

    }

    public boolean isRegistered() {
        return registrationLocation.isPresent();
    }

    Duration nextDelay(Duration lastDelay) {
        Duration newDelay = lastDelay.multipliedBy(2);

        if (newDelay.compareTo(minRetryDelay) < 0) {
            return minRetryDelay;
        }
        if (newDelay.compareTo(maxRetryDelay) > 0) {
            return maxRetryDelay;
        }
        return newDelay;
    }

}
