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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.Cache;
import com.mbed.coap.utils.CacheImpl;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if incoming request has been repeated
 *
 * @author szymon
 */
public class DuplicationDetector implements Runnable {
    public static final long DEFAULT_DUPLICATION_TIMEOUT_MILLIS = 30000; // Duplicate detection will end for a message after 30 seconds by default.
    public static final long DEFAULT_WARN_INTERVAL_MILLIS = 10000; //show warning message maximum every 10 seconds by default
    public static final long DEFAULT_CLEAN_INTERVAL_MILLIS = 10000; //clean expired messages every 10 seconds by default
    public static final CoapPacket EMPTY_COAP_PACKET = new CoapPacket(null);
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicationDetector.class.getName());

    private final long requestIdTimeout;
    private final Cache<CoapRequestId, CoapPacket> requestMap;

    public void setCleanDelayMili(long cleanDelayMili) {
        this.requestMap.setCleanIntervalMillis(cleanDelayMili);
    }

    public DuplicationDetector(TimeUnit unit,
            long duplicationTimeout,
            long maxSize,
            ScheduledExecutorService scheduledExecutor) {
        this(unit,
                duplicationTimeout,
                maxSize,
                DEFAULT_CLEAN_INTERVAL_MILLIS,
                DEFAULT_WARN_INTERVAL_MILLIS,
                scheduledExecutor);
    }

    public DuplicationDetector(TimeUnit unit,
            long duplicationTimeout,
            long maxSize,
            long cleanIntervalMillis,
            long warningMessageIntervalMillis,
            ScheduledExecutorService scheduledExecutor) {
        this(unit, duplicationTimeout, new CacheImpl("CoAP duplicate detection cache",
                maxSize,
                cleanIntervalMillis,
                warningMessageIntervalMillis,
                scheduledExecutor));
        LOGGER.debug("Duplicate detector: init (max traffic: " + (int) (maxSize / (duplicationTimeout / 1000.0d)) + " msg/sec)");
    }

    public DuplicationDetector(TimeUnit unit,
            long duplicationTimeout,
            Cache<CoapRequestId, CoapPacket> cache) {
        requestIdTimeout = TimeUnit.MILLISECONDS.convert(duplicationTimeout, unit);
        this.requestMap = cache;
    }

    public void start() {
        requestMap.start();
    }

    public void stop() {
        requestMap.stop();
    }

    public CoapPacket isMessageRepeated(CoapPacket request) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress(), requestIdTimeout);

        CoapPacket resp = requestMap.putIfAbsent(requestId, EMPTY_COAP_PACKET);
        if (resp != null) {
            return resp;
        }
        requestMap.cleanupBulk();
        return null;
    }

    public void putResponse(CoapPacket request, CoapPacket response) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress(), requestIdTimeout);
        requestMap.put(requestId, response);
    }

    @Override
    public void run() {
        requestMap.clean();
    }

}
