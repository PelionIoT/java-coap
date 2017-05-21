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
package com.mbed.coap.client;

import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.Callback;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationManager.class);
    private final Duration minRetryDelay;
    private final Duration maxRetryDelay;
    private final URI registrationUri;
    private final CoapClient client;
    private final ScheduledExecutorService scheduledExecutor;
    private final Supplier<String> registrationLinks;
    private volatile Optional<String> registrationLocation = Optional.empty();
    private volatile Duration lastRetryDelay = Duration.ZERO;


    public RegistrationManager(CoapServer server, URI registrationUri, ScheduledExecutorService scheduledExecutor,
            Duration minRetryDelay, Duration maxRetryDelay) {

        if (minRetryDelay.compareTo(maxRetryDelay) > 0) {
            throw new IllegalArgumentException();
        }

        this.client = new CoapClient(new InetSocketAddress(registrationUri.getHost(), registrationUri.getPort()), server);
        this.scheduledExecutor = scheduledExecutor;
        this.registrationUri = registrationUri;
        this.registrationLinks = () -> LinkFormatBuilder.toString(server.getResourceLinks());
        this.minRetryDelay = minRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    public RegistrationManager(CoapServer server, URI registrationUri, ScheduledExecutorService scheduledExecutor) {
        this(server, registrationUri, scheduledExecutor, Duration.ofSeconds(10), Duration.ofMinutes(5));
    }

    public void register() {
        try {
            client.resource(registrationUri.getPath())
                    .query(registrationUri.getQuery())
                    .payload(registrationLinks.get(), MediaTypes.CT_APPLICATION_LINK__FORMAT)
                    .post(new Callback<CoapPacket>() {
                        @Override
                        public void call(CoapPacket resp) {
                            if (resp.getCode().equals(Code.C201_CREATED)) {
                                registrationLocation = Optional.of(resp.headers().getLocationPath());
                                lastRetryDelay = Duration.ZERO;
                                scheduleUpdate(resp.headers().getMaxAgeValue());
                            } else {
                                registrationFailed();
                            }
                        }

                        @Override
                        public void callException(Exception ex) {
                            LOGGER.error(ex.getMessage(), ex);
                            registrationFailed();
                        }
                    });

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            registrationFailed();
        }
    }

    private void scheduleUpdate(long lifetime) {
        scheduledExecutor.schedule(this::updateRegistration, lifetime > 60 ? lifetime - 30 : lifetime, TimeUnit.SECONDS);
    }

    private void updateRegistration() {
        try {
            client.resource(registrationLocation.get()).post(new Callback<CoapPacket>() {
                @Override
                public void call(CoapPacket resp) {
                    if (resp.getCode().equals(Code.C201_CREATED) || resp.getCode().equals(Code.C204_CHANGED)) {
                        scheduleUpdate(resp.headers().getMaxAgeValue());
                    } else {
                        updateFailed();
                    }
                }

                @Override
                public void callException(Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    updateFailed();
                }
            });


        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            updateFailed();
        }

    }

    private void updateFailed() {
        registrationLocation = Optional.empty();
        register();
    }

    private void registrationFailed() {
        registrationLocation = Optional.empty();
        lastRetryDelay = nextDelay(lastRetryDelay);
        scheduledExecutor.schedule(this::register, lastRetryDelay.getSeconds(), TimeUnit.SECONDS);
        LOGGER.debug("Registration failed. Scheduled re-try in " + lastRetryDelay + "s");
    }


    public void removeRegistration() {
        registrationLocation.ifPresent(loc -> {
            try {
                client.resource(loc).delete();
                //we can ignore result
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
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
