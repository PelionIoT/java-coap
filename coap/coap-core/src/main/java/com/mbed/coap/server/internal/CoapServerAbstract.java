/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapErrorCallback;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public abstract class CoapServerAbstract implements CoapReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerAbstract.class.getName());
    private static final long DELAYED_TRANSACTION_TIMEOUT_MS = 120000; //2 minutes
    protected long delayedTransactionTimeout = DELAYED_TRANSACTION_TIMEOUT_MS;
    protected TransmissionTimeout transmissionTimeout;
    protected CoapErrorCallback errorCallback;

    long getDelayedTransactionTimeout() {
        return delayedTransactionTimeout;
    }

    TransmissionTimeout getTransmissionTimeout() {
        return transmissionTimeout;
    }

    /**
     * Sends CoapPacket to specified destination UDP address.
     *
     * @param coapPacket CoAP packet
     * @param adr destination address
     * @param tranContext transport context
     * @throws CoapException exception from CoAP layer
     * @throws IOException   exception from transport layer
     */
    protected final void send(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        if (coapPacket.getMessageType() == MessageType.NonConfirmable) {
            coapPacket.setMessageId(getNextMID());
        }
        sendPacket(coapPacket, adr, tranContext);
    }

    protected abstract void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

    protected abstract void handleException(byte[] packet, CoapException exception, TransportContext transportContext);

    protected abstract int getNextMID();

    protected abstract DuplicationDetector getDuplicationDetector();

    protected final void putToDuplicationDetector(CoapPacket request, CoapPacket response) {
        if (getDuplicationDetector() != null) {
            getDuplicationDetector().putResponse(request, response);
        }
    }

    protected void sendResponse(CoapExchange exchange) {
        try {
            CoapPacket resp = exchange.getResponse();
            if (resp == null) {
                //nothing to send
                return;
            }
            send(resp, exchange.getRemoteAddress(), exchange.getResponseTransportContext());
            putToDuplicationDetector(exchange.getRequest(), resp);
        } catch (CoapException ex) {
            LOGGER.warn(ex.getMessage());
            try {
                CoapPacket errorResp = exchange.getRequest().createResponse(Code.C500_INTERNAL_SERVER_ERROR);
                send(errorResp, exchange.getRemoteAddress(), exchange.getResponseTransportContext());
                putToDuplicationDetector(exchange.getRequest(), errorResp);
            } catch (CoapException | IOException ex1) {
                //impossible ;)
                LOGGER.error(ex1.getMessage(), ex1);
            }
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }
}
