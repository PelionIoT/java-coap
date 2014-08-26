/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
public class TransactionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionManager.class);
    private final ConcurrentHashMap<CoapTransactionId, CoapTransaction> transactions = new ConcurrentHashMap<>();

    public void add(CoapTransaction trans) {
        transactions.put(trans.getTransactionId(), trans);
    }

    public int getNumberOfTransactions() {
        return transactions.size();
    }

    public Set<CoapTransactionId> getTransactions() {
        return transactions.keySet();
    }

    public CoapTransaction find(CoapTransactionId coapTransId) {
        CoapTransaction trans = transactions.get(coapTransId);
        if (trans == null) {
            //search for multicast transactions
            for (Map.Entry<CoapTransactionId, CoapTransaction> entry : transactions.entrySet()) {
                CoapTransactionId t = entry.getKey();
                if (t instanceof MulticastTransactionId && t.equals(coapTransId)) {
                    trans = entry.getValue();
                    LOGGER.trace("Found transactionId match for multicast request [{}", t);
                    break;
                }
            }
        }
        return trans;
    }

    public void remove(CoapTransactionId coapTransId) {
        transactions.remove(coapTransId);
    }

    public Collection<CoapTransaction> findTimeoutTransactions(final long currentTime) {
        final Collection<CoapTransaction> transTimeOut = new LinkedList<>();
        for (Map.Entry<CoapTransactionId, CoapTransaction> entry : transactions.entrySet()) {
            if (entry.getValue().isTimedOut(currentTime)) {
                transTimeOut.add(entry.getValue());
            }
        }
        return transTimeOut;
    }

}
