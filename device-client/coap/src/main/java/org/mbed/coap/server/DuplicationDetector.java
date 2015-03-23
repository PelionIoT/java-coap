/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.packet.CoapPacket;

/**
 * Checks if incoming request has been repeated
 *
 * @author szymon
 */
class DuplicationDetector implements Runnable {

    public static final CoapPacket EMPTY_COAP_PACKET = new CoapPacket(null);
    private static final Logger LOGGER = Logger.getLogger(DuplicationDetector.class.getName());
    private static final long DEFAULT_REQUEST_ID_TIMEOUT = 30000;
    private static final int DEFAULT_MAX_SIZE = 100000;
    //
    private final Lock REDUCE_LOCK = new ReentrantLock();
    private final long requestIdTimeout;
    private final long maxSize;
    private final ConcurrentMap<CoapRequestId, CoapPacket> requestMap = new ConcurrentHashMap<>();
    private long cleanDelayMili = 10000;
    private ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> cleanWorkerFut;
    private long overSizeMargin = 100;

    public void setCleanDelayMili(long cleanDelayMili) {
        this.cleanDelayMili = cleanDelayMili;
    }

    public DuplicationDetector(TimeUnit unit, long duplicationTimeout, long maxSize, ScheduledExecutorService scheduledExecutor) {
        requestIdTimeout = TimeUnit.MILLISECONDS.convert(duplicationTimeout, unit);
        this.maxSize = maxSize;
        this.overSizeMargin = maxSize / 100; //1%
        this.scheduledExecutor = scheduledExecutor;
        LOGGER.fine("Coap duplicate detector init (max traffic: " + (int) (maxSize / (requestIdTimeout / 1000.0d)) + " msg/sec)");
    }

    public DuplicationDetector() {
        this(TimeUnit.MILLISECONDS, DEFAULT_REQUEST_ID_TIMEOUT, DEFAULT_MAX_SIZE, Executors.newSingleThreadScheduledExecutor());
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
                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                }
                LOGGER.warning("CoAP request duplicate list has reached max size (" + maxSize + "), reduced by " + overSizeMargin);
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
        if (LOGGER.isLoggable(Level.FINEST) && removedItems > 0) {
            LOGGER.finest("CoAP request duplicate list, non valid items removed: " + removedItems + " ");
        }
    }

    private static class CoapRequestId {

        private final int mid;
        private final InetSocketAddress sourceAddress;
        private final long validTimeout;

        public CoapRequestId(int mid, InetSocketAddress sourceAddress, long requestIdTimeout) {
            this.mid = mid;
            this.sourceAddress = sourceAddress;
            this.validTimeout = System.currentTimeMillis() + requestIdTimeout;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CoapRequestId)) {
                return false;
            }

            CoapRequestId objRequestId = (CoapRequestId) obj;
            return (mid == objRequestId.mid && sourceAddress.equals(objRequestId.sourceAddress));
        }

        public boolean isValid(final long timestamp) {
            return validTimeout > timestamp;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 73 * hash + this.mid;
            hash = 73 * hash + (this.sourceAddress != null ? this.sourceAddress.hashCode() : 0);
            return hash;
        }
    }
}
