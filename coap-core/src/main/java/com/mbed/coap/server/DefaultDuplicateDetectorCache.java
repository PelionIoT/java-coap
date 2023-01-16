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

import com.mbed.coap.packet.CoapPacket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDuplicateDetectorCache implements PutOnlyMap<CoapRequestId, CoapPacket> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDuplicateDetectorCache.class);
    private static final int DEFAULT_DUPLICATE_DETECTOR_CLEAN_INTERVAL_MILLIS = 10000;
    private static final int DEFAULT_DUPLICATE_DETECTOR_WARNING_INTERVAL_MILLIS = 10000;
    private static final int DEFAULT_DUPLICATE_DETECTOR_DETECTION_TIME_MILLIS = 30000;

    private final Lock REDUCE_LOCK = new ReentrantLock();
    private final ConcurrentHashMap<CoapRequestId, CoapPacket> underlying;
    private final long maxSize;
    private final long overSizeMargin;
    private final long warnIntervalMillis;
    private long nextWarnMessage;
    private final long duplicateDetectionTimeMillis;
    private final String cacheName;
    private final ScheduledFuture<?> cleanWorkerFut;

    public DefaultDuplicateDetectorCache(String cacheName,
            long maxSize,
            long duplicateDetectionTimeMillis,
            long cleanIntervalMillis,
            long warnIntervalMillis,
            ScheduledExecutorService scheduledExecutor) {
        this.cacheName = cacheName;
        this.duplicateDetectionTimeMillis = duplicateDetectionTimeMillis;
        this.maxSize = maxSize;
        this.warnIntervalMillis = warnIntervalMillis;
        this.overSizeMargin = maxSize / 100; //1%
        underlying = new ConcurrentHashMap<>();

        cleanWorkerFut = scheduledExecutor.scheduleWithFixedDelay(this::clean, cleanIntervalMillis, cleanIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public DefaultDuplicateDetectorCache(String cacheName, long duplicateDetectionTimeMillis, ScheduledExecutorService scheduledExecutor) {
        this(
                cacheName,
                duplicateDetectionTimeMillis,
                DEFAULT_DUPLICATE_DETECTOR_DETECTION_TIME_MILLIS,
                DEFAULT_DUPLICATE_DETECTOR_CLEAN_INTERVAL_MILLIS,
                DEFAULT_DUPLICATE_DETECTOR_WARNING_INTERVAL_MILLIS,
                scheduledExecutor
        );
    }

    @Override
    public void stop() {
        cleanWorkerFut.cancel(true);
    }

    @Override
    public CoapPacket putIfAbsent(CoapRequestId key, CoapPacket value) {
        CoapPacket result = underlying.putIfAbsent(key, value);
        // Cleanup only if new entry was added to map.
        if (result == null) {
            cleanupBulk();
        }
        return result;

    }

    @Override
    public void put(CoapRequestId key, CoapPacket value) {
        underlying.put(key, value);
    }

    public void clean() {
        int removedItems = 0;
        Iterator<CoapRequestId> it = underlying.keySet().iterator();
        final long currentTimeMillis = System.currentTimeMillis();
        while (it.hasNext()) {
            if (currentTimeMillis - it.next().getCreatedTimestampMillis() > duplicateDetectionTimeMillis) {
                it.remove();
                removedItems++;
            }
        }
        if (LOGGER.isTraceEnabled() && removedItems > 0) {
            LOGGER.trace("CoAP request duplicate list, non valid items removed: " + removedItems + " ");
        }
    }

    public void cleanupBulk() {
        if (underlying.size() > maxSize + overSizeMargin && REDUCE_LOCK.tryLock()) {
            try {
                //reduce map size in bulk
                Iterator<CoapRequestId> it = underlying.keySet().iterator();
                try {
                    for (int i = 0; i <= overSizeMargin && it.hasNext(); i++) {
                        it.next();
                        it.remove();
                        //requestList.remove(it.next());
                    }
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }

                if (nextWarnMessage < System.currentTimeMillis()) {
                    LOGGER.warn(cacheName + " has reached max size (" + maxSize + "), reduced by " + overSizeMargin);
                    nextWarnMessage = System.currentTimeMillis() + warnIntervalMillis;
                }
            } finally {
                REDUCE_LOCK.unlock();
            }
        }

    }
}
