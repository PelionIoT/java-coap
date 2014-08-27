/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.io.IOException;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.Callback;

/**
 * Describes CoAP transaction
 */
public class CoapTransaction {

    protected Callback<CoapPacket> callback;
    private long timeout = -1;
    protected byte retrAttempts;
    protected CoapPacket coapRequest;
    private CoapTransactionId transId;
    private DelayedTransactionId delayedTransId;
    private final CoapServerAbstract coapServer;
    private final TransportContext transContext;

    public CoapTransaction(Callback<CoapPacket> callback, CoapPacket coapRequest, final CoapServerAbstract coapServer, TransportContext transContext) {
        this.coapServer = coapServer;
        this.callback = callback;
        this.coapRequest = coapRequest;
        if (coapRequest.getOtherEndAddress().getAddress().isMulticastAddress()) {
            this.transId = new MulticastTransactionId(coapRequest);
        } else {
            this.transId = new CoapTransactionId(coapRequest);
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
        long nextTimeout = 0;
        if (coapRequest.getOtherEndAddress().getAddress().isMulticastAddress()) {
            nextTimeout = coapServer.getTransmissionTimeout().getMulticastTimeout(this.retrAttempts);
        } else {
            nextTimeout = coapServer.getTransmissionTimeout().getTimeout(this.retrAttempts);
        }
        if (nextTimeout <= 0) {
            return false;
        }
        coapServer.send(coapRequest, coapRequest.getOtherEndAddress(), transContext);
        this.timeout = currentTime + nextTimeout;
        return true;
    }

    public Callback<CoapPacket> getCallback() {
        return callback;
    }

    public CoapPacket getCoapRequest() {
        return coapRequest;
    }

}
