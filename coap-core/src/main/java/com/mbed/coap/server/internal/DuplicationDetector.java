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
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if incoming request has been repeated
 *
 * @author szymon
 */
public class DuplicationDetector implements Runnable {

    public static final CoapPacket EMPTY_COAP_PACKET = new CoapPacket(null);
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicationDetector.class.getName());
    public static final int WARN_FREQ_MILLI = 10000; //show warning message maximum every 10 seconds

    private final Lock REDUCE_LOCK = new ReentrantLock();
    private final long requestIdTimeout;
    private final long maxSize;
    private final ConcurrentMap<CoapRequestId, CoapPacket> requestMap = new ConcurrentHashMap<>();
    private long cleanDelayMili = 10000;
    private final ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> cleanWorkerFut;
    private final long overSizeMargin;
    private long nextWarnMessage;

    public void setCleanDelayMili(long cleanDelayMili) {
        this.cleanDelayMili = cleanDelayMili;
    }

    public DuplicationDetector(TimeUnit unit, long duplicationTimeout, long maxSize, ScheduledExecutorService scheduledExecutor) {
        requestIdTimeout = TimeUnit.MILLISECONDS.convert(duplicationTimeout, unit);
        this.maxSize = maxSize;
        this.overSizeMargin = maxSize / 100; //1%
        this.scheduledExecutor = scheduledExecutor;
        LOGGER.debug("Coap duplicate detector init (max traffic: " + (int) (maxSize / (requestIdTimeout / 1000.0d)) + " msg/sec)");
    }

    public void start() {
        cleanWorkerFut = scheduledExecutor.scheduleWithFixedDelay(this, cleanDelayMili, cleanDelayMili, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        cleanWorkerFut.cancel(true);
    }

    public CoapPacket isMessageRepeated(CoapPacket request) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress(), requestIdTimeout);

        CoapPacket resp = requestMap.putIfAbsent(requestId, EMPTY_COAP_PACKET);
        if (resp != null) {
            return resp;
        }
        if (requestMap.size() > maxSize + overSizeMargin && REDUCE_LOCK.tryLock()) {
            try {
                //reduce map size in bulk
                Iterator<CoapRequestId> it = requestMap.keySet().iterator();
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
                    LOGGER.warn("CoAP request duplicate list has reached max size (" + maxSize + "), reduced by " + overSizeMargin);
                    nextWarnMessage = System.currentTimeMillis() + WARN_FREQ_MILLI;
                }
            } finally {
                REDUCE_LOCK.unlock();
            }
        }
        return null;
    }

    public void putResponse(CoapPacket request, CoapPacket response) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress(), requestIdTimeout);
        requestMap.put(requestId, response);
    }

    @Override
    public void run() {
        int removedItems = 0;
        Iterator<CoapRequestId> it = requestMap.keySet().iterator();
        final long currentTimeMillis = System.currentTimeMillis();
        while (it.hasNext()) {
            if (!it.next().isValid(currentTimeMillis)) {
                it.remove();
                removedItems++;
            }
        }
        if (LOGGER.isTraceEnabled() && removedItems > 0) {
            LOGGER.trace("CoAP request duplicate list, non valid items removed: " + removedItems + " ");
        }
    }

    static class CoapRequestId {

        private final int mid;
        private final InetSocketAddress sourceAddress;
        private final transient long validTimeout;

        public CoapRequestId(int mid, InetSocketAddress sourceAddress, long requestIdTimeout) {
            this.mid = mid;
            this.sourceAddress = sourceAddress;
            this.validTimeout = System.currentTimeMillis() + requestIdTimeout;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            CoapRequestId objRequestId = (CoapRequestId) obj;

            if (mid != objRequestId.mid) {
                return false;
            }
            return sourceAddress != null ? sourceAddress.equals(objRequestId.sourceAddress) : objRequestId.sourceAddress == null;
        }

        public boolean isValid(final long timestamp) {
            return validTimeout > timestamp;
        }

        @Override
        public int hashCode() {
            int result = mid;
            result = 31 * result + (sourceAddress != null ? sourceAddress.hashCode() : 0);
            return result;
        }
    }
}
