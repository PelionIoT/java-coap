/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MessageType;

/**
 * @author szymon
 */
public class TransactionManager {

    private static final Logger LOGGER = Logger.getLogger(TransactionManager.class.getName());
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
                    LOGGER.finest("Found transactionId match for multicast request " + t);
                    break;
                }
            }
        }
        return trans;
    }

    public CoapTransaction findMatchForSeparateResponse(CoapPacket req) {
        if ((req.getMessageType() == MessageType.Confirmable || req.getMessageType() == MessageType.NonConfirmable)
                && req.getCode() != null && req.getToken().length > 0) {

            return transactions.values()
                    .stream()
                    .filter(trans -> isMatchForSeparateResponse(trans, req))
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
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

    private static boolean isMatchForSeparateResponse(CoapTransaction trans, CoapPacket packet) {
        return (packet.getMessageType() == MessageType.Confirmable || packet.getMessageType() == MessageType.NonConfirmable)
                && packet.getCode() != null
                && trans.coapRequest.getRemoteAddress().equals(packet.getRemoteAddress())
                && Arrays.equals(trans.coapRequest.getToken(), packet.getToken());
    }
}
