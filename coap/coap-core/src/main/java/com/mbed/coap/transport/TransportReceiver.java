/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ReceiveException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.ByteArrayBackedOutputStream;
import com.mbed.coap.utils.HexArray;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport receiver interface.
 *
 * @author szymon
 */
public interface TransportReceiver {

    /**
     * Handle data received from transport layer. Implementation MUST not block.
     *
     * @param adr source address
     * @param data packet raw data
     * @param transportContext transport context
     * @throws ReceiveException for anticipated errors in handling the receive, not to be regarded as bugs (so e.g. no
     *                          stacktrace is needed in the catch)
     */
    void onReceive(InetSocketAddress adr, byte[] data, TransportContext transportContext) throws ReceiveException;

    /**
     * Connection with a remote has been closed. It is used only by transport that supports connection (like TCP).
     *
     * @param remoteAddress remote address
     */
    void onConnectionClosed(InetSocketAddress remoteAddress);


    class CoapTransportFromTransportConnector implements TransportReceiver, CoapTransport {
        private static final Logger LOGGER = LoggerFactory.getLogger(CoapTransportFromTransportConnector.class);

        private final TransportConnector transport;
        private final CoapServer coapServer;

        public CoapTransportFromTransportConnector(CoapServer server, TransportConnector transport) {
            this.transport = transport;
            this.coapServer = server;
        }

        @Override
        public void start(CoapReceiver server) throws IOException {
            transport.start(this);
        }

        @Override
        public void stop() {
            transport.stop();
        }


        @Override
        public void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
            ByteArrayBackedOutputStream stream = new ByteArrayBackedOutputStream(coapPacket.getPayload() != null ? coapPacket.getPayload().length + 8 : 16);
            coapPacket.writeTo(stream);
            transport.send(stream.getByteArray(), stream.size(), adr, tranContext);
        }

        @Override
        public void onReceive(InetSocketAddress address, byte[] data, TransportContext transportContext) {
            try {
                CoapPacket coapPkt = CoapPacket.read(data, data.length, address);
                coapServer.handle(coapPkt, transportContext);
            } catch (CoapException ex) {
                LOGGER.warn("Malformed coap message source: " + address + ", size:" + data.length);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Malformed coap message payload: " + HexArray.toHex(data));
                }
                coapServer.handleException(data, ex, transportContext);
            }

        }

        @Override
        public void onConnectionClosed(InetSocketAddress remoteAddress) {
            LOGGER.debug("Connection with " + remoteAddress + " was closed");
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return transport.getLocalSocketAddress();
        }
    }
}
