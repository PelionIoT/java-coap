/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
public class DelayedTransactionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelayedTransactionManager.class);
    private final ConcurrentMap<DelayedTransactionId, CoapTransaction> transactions = new ConcurrentHashMap<DelayedTransactionId, CoapTransaction>();

    public void add(DelayedTransactionId delayedTransactionId, CoapTransaction trans) {
        trans.setDelayedTransId(delayedTransactionId);
        transactions.put(delayedTransactionId, trans);
        LOGGER.trace("Added delayed transaction: {}", delayedTransactionId);
    }

    public CoapTransaction find(DelayedTransactionId delayedTransactionId) {
        return transactions.get(delayedTransactionId);
    }

    public void remove(DelayedTransactionId delayedTransactionId) {
        transactions.remove(delayedTransactionId);
    }

    public Collection<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        Collection<CoapTransaction> transTimeOut = new LinkedList<CoapTransaction>();
        for (Map.Entry<DelayedTransactionId, CoapTransaction> trans : transactions.entrySet()) {
            if (trans.getValue().isTimedOut(currentTime)) {
                transTimeOut.add(trans.getValue());
            }
        }
        return transTimeOut;
    }

}
