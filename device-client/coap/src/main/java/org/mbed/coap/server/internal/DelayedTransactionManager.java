/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author szymon
 */
public class DelayedTransactionManager {

    private static final Logger LOGGER = Logger.getLogger(DelayedTransactionManager.class.getName());
    private final ConcurrentMap<DelayedTransactionId, CoapTransaction> transactions = new ConcurrentHashMap<>();

    public void add(DelayedTransactionId delayedTransactionId, CoapTransaction trans) {
        trans.setDelayedTransId(delayedTransactionId);
        transactions.put(delayedTransactionId, trans);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Added delayed transaction: " + delayedTransactionId);
        }
    }

    public CoapTransaction find(DelayedTransactionId delayedTransactionId) {
        return transactions.get(delayedTransactionId);
    }

    public void remove(DelayedTransactionId delayedTransactionId) {
        transactions.remove(delayedTransactionId);
    }

    public Collection<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        Collection<CoapTransaction> transTimeOut = new LinkedList<>();
        for (Map.Entry<DelayedTransactionId, CoapTransaction> trans : transactions.entrySet()) {
            if (trans.getValue().isTimedOut(currentTime)) {
                transTimeOut.add(trans.getValue());
            }
        }
        return transTimeOut;
    }

}
