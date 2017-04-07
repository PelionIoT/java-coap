/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Describes CoAP transaction
 */
public class CoapTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTransaction.class.getName());

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
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(".ctor #1: MID:" + coapRequest.getMessageId());
        }
        this.transactionPriority = transactionPriority;
        if (coapRequest.getRemoteAddress().getAddress().isMulticastAddress()) {
            this.transId = new MulticastTransactionId(coapRequest);
        } else {
            this.transId = new CoapTransactionId(coapRequest);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(".ctor #1: MID:" + coapRequest.getMessageId() + ", tid=" + transId);
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
        if (LOGGER.isTraceEnabled()) {
            logSend(currentTime, "begin");
        }
        long nextTimeout = 0;
        if (coapRequest.getRemoteAddress().getAddress().isMulticastAddress()) {
            nextTimeout = coapServer.getTransmissionTimeout().getMulticastTimeout(this.retrAttempts);
        } else {
            nextTimeout = coapServer.getTransmissionTimeout().getTimeout(this.retrAttempts);
        }
        if (LOGGER.isTraceEnabled()) {
            logSend(currentTime, "nextTimeout=" + nextTimeout);
        }
        if (nextTimeout <= 0) {
            if (LOGGER.isTraceEnabled()) {
                logSend(currentTime, "failed, timeout");
            }
            return false;
        }
        if (LOGGER.isTraceEnabled()) {
            logSend(currentTime, "sending");
        }
        isActive = true;
        coapServer.send(coapRequest, coapRequest.getRemoteAddress(), transContext);
        if (LOGGER.isTraceEnabled()) {
            logSend(currentTime, "sent");
        }
        this.timeout = currentTime + nextTimeout;
        return true;
    }

    private void logSend(long currentTime, String distinguisher) {
        LOGGER.trace("send(" + currentTime + "): " + toString() + ", MID:" + coapRequest.getMessageId() + ", attempt=" + retrAttempts + ", " + distinguisher);
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
