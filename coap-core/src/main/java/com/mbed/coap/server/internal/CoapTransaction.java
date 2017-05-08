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
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java8.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Describes CoAP transaction
 */
public class CoapTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTransaction.class.getName());

    protected RequestCallback callback;
    private long timeout = -1;
    protected byte retrAttempts;
    protected CoapPacket coapRequest;
    private CoapTransactionId transId;
    private DelayedTransactionId delayedTransId;
    private final CoapUdpMessaging coapServer;
    private final TransportContext transContext;
    private final Priority transactionPriority;
    private final Consumer<CoapTransactionId> sendErrConsumer;
    private boolean isActive;

    public CoapTransaction(RequestCallback callback, CoapPacket coapRequest, final CoapUdpMessaging coapServer, TransportContext transContext, Consumer<CoapTransactionId> sendErrConsumer) {
        this(callback, coapRequest, coapServer, transContext, Priority.NORMAL, sendErrConsumer);
    }

    public CoapTransaction(RequestCallback callback, CoapPacket coapRequest, final CoapUdpMessaging coapServer, TransportContext transContext, Priority transactionPriority, Consumer<CoapTransactionId> sendErrConsumer) {
        if (callback == null) {
            throw new NullPointerException();
        }
        this.sendErrConsumer = sendErrConsumer;
        this.coapServer = coapServer;
        this.callback = callback;
        this.coapRequest = coapRequest;
        this.transactionPriority = transactionPriority;
        this.transId = new CoapTransactionId(coapRequest);
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


    public final boolean send() {
        return send(System.currentTimeMillis());
    }

    public final boolean send(final long currentTime) {
        this.retrAttempts++;
        long nextTimeout;
        if (coapRequest.getRemoteAddress().getAddress().isMulticastAddress()) {
            nextTimeout = coapServer.getTransmissionTimeout().getMulticastTimeout(this.retrAttempts);
        } else {
            nextTimeout = coapServer.getTransmissionTimeout().getTimeout(this.retrAttempts);
        }
        if (nextTimeout <= 0) {
            return false;
        }
        isActive = true;
        coapServer.send(coapRequest, coapRequest.getRemoteAddress(), transContext)
                .whenComplete((wasSent, maybeError) -> onSend(maybeError));

        this.timeout = currentTime + nextTimeout;
        return true;
    }

    private void onSend(Throwable maybeError) {
        if (maybeError == null) {
            callback.onSent();
        } else {
            sendErrConsumer.accept(transId);
            callback.callException(((Exception) maybeError));
        }
    }

    public Callback<CoapPacket> getCallback() {
        return callback;
    }

    public void invokeCallback(CoapPacket packet) {
        try {
            callback.call(packet);
        } catch (Exception ex) {
            LOGGER.error("Error while handling callback: " + ex.getMessage(), ex);
        }
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
