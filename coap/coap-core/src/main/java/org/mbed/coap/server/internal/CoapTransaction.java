/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.Callback;

/**
 * Describes CoAP transaction
 */
public class CoapTransaction {
    private static final Logger LOGGER = Logger.getLogger(CoapTransaction.class.getName());

    protected Callback<CoapPacket> callback;
    private long timeout = -1;
    protected byte retrAttempts;
    protected CoapPacket coapRequest;
    private CoapTransactionId transId;
    private DelayedTransactionId delayedTransId;
    private final CoapServerAbstract coapServer;
    private final TransportContext transContext;
    private final Priority transactionPriority;
    private boolean isActive;

    public CoapTransaction(Callback<CoapPacket> callback, CoapPacket coapRequest, final CoapServerAbstract coapServer, TransportContext transContext) {
        this(callback, coapRequest, coapServer, transContext, Priority.NORMAL);
    }

    public CoapTransaction(Callback<CoapPacket> callback, CoapPacket coapRequest, final CoapServerAbstract coapServer, TransportContext transContext, Priority transactionPriority) {
        this.coapServer = coapServer;
        this.callback = callback;
        this.coapRequest = coapRequest;
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(".ctor #1: MID:" + coapRequest.getMessageId());
        }
        this.transactionPriority = transactionPriority;
        if (coapRequest.getRemoteAddress().getAddress().isMulticastAddress()) {
            this.transId = new MulticastTransactionId(coapRequest);
        } else {
            this.transId = new CoapTransactionId(coapRequest);
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(".ctor #1: MID:" + coapRequest.getMessageId() + ", tid=" + transId);
        }
        this.retrAttempts = 0;
        this.transContext = transContext;
    }

    public boolean isTimedOut(final long currentTimeMillis) {
        return timeout > 0 && currentTimeMillis >= timeout;
    }

    public CoapTransactionId getTransactionId() {
        return transId;
    }

    public Priority getTransactionPriority() {
        return transactionPriority;
    }

    public void setDelayedTransId(DelayedTransactionId delayedTransId) {
        this.delayedTransId = delayedTransId;
        this.transId = null;
        this.timeout = System.currentTimeMillis() + coapServer.getDelayedTransactionTimeout();
    }

    public DelayedTransactionId getDelayedTransId() {
        return delayedTransId;
    }

    public final boolean send(final long currentTime) throws CoapException, IOException {
        this.retrAttempts++;
        if (LOGGER.isLoggable(Level.FINEST)) {
            logSend(currentTime, "begin");
        }
        long nextTimeout = 0;
        if (coapRequest.getRemoteAddress().getAddress().isMulticastAddress()) {
            nextTimeout = coapServer.getTransmissionTimeout().getMulticastTimeout(this.retrAttempts);
        } else {
            nextTimeout = coapServer.getTransmissionTimeout().getTimeout(this.retrAttempts);
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            logSend(currentTime, "nextTimeout=" + nextTimeout);
        }
        if (nextTimeout <= 0) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                logSend(currentTime, "failed, timeout");
            }
            return false;
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            logSend(currentTime, "sending");
        }
        isActive = true;
        coapServer.send(coapRequest, coapRequest.getRemoteAddress(), transContext);
        if (LOGGER.isLoggable(Level.FINEST)) {
            logSend(currentTime, "sent");
        }
        this.timeout = currentTime + nextTimeout;
        return true;
    }

    private void logSend(long currentTime, String distinguisher) {
        LOGGER.finest("send(" + currentTime + "): " + toString() + ", MID:" + coapRequest.getMessageId() + ", attempt=" + retrAttempts + ", " + distinguisher);
    }

    public Callback<CoapPacket> getCallback() {
        return callback;
    }

    public CoapPacket getCoapRequest() {
        return coapRequest;
    }

    public boolean isActive() {
        return isActive;
    }

    //ONY FOR TESTS, package access only
    CoapTransaction makeActiveForTests() {
        this.isActive = true;
        return this;
    }


    public enum Priority {
        HIGH,
        NORMAL,
        LOW;
    }

    @Override
    public String toString() {
        return "CoapTransaction{ " +
                transId +
                ", MID:" + coapRequest.getMessageId() +
                ", active=" + isActive +
                ", this=" + System.identityHashCode(this) +
                ", pkt=" + System.identityHashCode(coapRequest) +
                ", tid=" + System.identityHashCode(transId) +
                (delayedTransId != null ? " , delayedId= '" + delayedTransId + "'" : "") +
                " , prio=" + transactionPriority +
                (timeout >= 0 ? ", timeout=" + timeout : "") +
                '}';
    }
}
