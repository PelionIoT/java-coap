/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapErrorCallback;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
public abstract class CoapServerAbstract implements TransportReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerAbstract.class);
    private static final int DELAYED_TRANSACTION_TIMEOUT_MS = 120000; //2 minutes
    protected int delayedTransactionTimeout = DELAYED_TRANSACTION_TIMEOUT_MS;
    protected TransmissionTimeout transmissionTimeout;
    protected Executor executor;
    protected CoapErrorCallback errorCallback;

    int getDelayedTransactionTimeout() {
        return delayedTransactionTimeout;
    }

    TransmissionTimeout getTransmissionTimeout() {
        return transmissionTimeout;
    }

    @Override
    public void onReceive(InetSocketAddress adr, ByteBuffer buffer, TransportContext transportContext) {
        try {
            executor.execute(new MessageHandlerTask(buffer, adr, transportContext, this));
        } catch (RejectedExecutionException ex) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Executor queue is full, message from " + adr + " is rejected");
            }
            if (LOGGER.isTraceEnabled() && executor instanceof ThreadPoolExecutor) {
                LOGGER.trace("Executor Queue remaining capacity " + ((ThreadPoolExecutor) executor).getQueue().remainingCapacity()
                        + " out of " + ((ThreadPoolExecutor) executor).getQueue().size());
            }
        }
    }

    /**
     * Sends CoapPacket to specified destination UDP address.
     *
     * @param coapPacket CoAP packet
     * @param adr destination address
     * @param tranContext transport context
     * @throws CoapException
     * @throws IOException
     */
    protected abstract void send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

    protected abstract void handle(CoapPacket packet, TransportContext transportContext);

    protected abstract void handleException(byte[] packet, CoapException exception, TransportContext transportContext);

}
