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
package com.mbed.coap.server.messaging;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.PutOnlyMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDuplicateDetectorCache<K extends CoapRequestId, V extends CoapPacket> implements PutOnlyMap<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicationDetector.class.getName());
    private final Lock REDUCE_LOCK = new ReentrantLock();
    private final ConcurrentHashMap<K, V> underlying;
    private final long maxSize;
    private final long overSizeMargin;
    private final long warnIntervalMillis;
    private final long cleanIntervalMillis;
    private long nextWarnMessage;
    private final long duplicateDetectionTimeMillis;
    private final String cacheName;
    private final ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> cleanWorkerFut;

    public DefaultDuplicateDetectorCache(String cacheName,
            long maxSize,
            long duplicateDetectionTimeMillis,
            long cleanIntervalMillis,
            long warnIntervalMillis,
            ScheduledExecutorService scheduledExecutor) {
        this.cacheName = cacheName;
        this.duplicateDetectionTimeMillis = duplicateDetectionTimeMillis;
        this.maxSize = maxSize;
        this.cleanIntervalMillis = cleanIntervalMillis;
        this.warnIntervalMillis = warnIntervalMillis;
        this.scheduledExecutor = scheduledExecutor;
        this.overSizeMargin = maxSize / 100; //1%
        underlying = new ConcurrentHashMap<>();
    }

    public void start() {
        cleanWorkerFut = scheduledExecutor.scheduleWithFixedDelay(() -> clean(), cleanIntervalMillis, cleanIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (cleanWorkerFut != null) {
            cleanWorkerFut.cancel(true);
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V result = underlying.putIfAbsent(key, value);
        // Cleanup only if new entry was added to map.
        if (result == null) {
            cleanupBulk();
        }
        return result;

    }

    @Override
    public void put(K key, V value) {
        underlying.put(key, value);
    }

    public void clean() {
        int removedItems = 0;
        Iterator<K> it = underlying.keySet().iterator();
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
                Iterator<K> it = underlying.keySet().iterator();
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
