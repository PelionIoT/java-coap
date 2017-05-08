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
package com.mbed.coap.transport.javassl;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLSocketClientTransport implements CoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSocketClientTransport.class);

    private final InetSocketAddress destination;
    private OutputStream outputStream;
    private InputStream inputStream;
    private SSLSocket sslSocket;
    private Thread readerThread;
    private SSLSocketFactory socketFactory;

    public SSLSocketClientTransport(InetSocketAddress destination, SSLSocketFactory socketFactory) {
        this.destination = destination;
        this.socketFactory = socketFactory;
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        sslSocket = (SSLSocket) socketFactory.createSocket(destination.getAddress(), destination.getPort());

        sslSocket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                    try {
                        LOGGER.debug("Connected [" + handshakeCompletedEvent.getSource() + ", " + sslSocket.getSession().getPeerCertificateChain()[0].getSubjectDN() + "]");
                    } catch (SSLPeerUnverifiedException e) {
                        LOGGER.warn(e.getMessage(), e);
                    }
                }
        );
        sslSocket.startHandshake();

        synchronized (this) {
            outputStream = new BufferedOutputStream(sslSocket.getOutputStream());
        }
        inputStream = new BufferedInputStream(sslSocket.getInputStream(), 1024);

        readerThread = new Thread(() -> loopReading(coapReceiver), "tls-client-read");
        readerThread.start();
    }

    private void loopReading(CoapReceiver coapServer) {
        try {
            while (!sslSocket.isClosed()) {
                try {
                    final CoapPacket coapPacket = CoapPacket.deserialize(((InetSocketAddress) sslSocket.getRemoteSocketAddress()), inputStream);
                    coapServer.handle(coapPacket, TransportContext.NULL);
                } catch (CoapException e) {
                    if (e.getCause() != null && e.getCause() instanceof IOException) {
                        throw ((IOException) e.getCause());
                    }
                    LOGGER.warn("Closing socket connection, due to parsing error: " + e.getMessage());
                    sslSocket.close();
                }
            }
        } catch (Exception ex) {
            if (!ex.getMessage().startsWith("Socket closed")) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public synchronized void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        if (adr != this.destination) {
            throw new IllegalStateException("No connection with: " + adr);
        }
        coapPacket.writeTo(outputStream);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return ((InetSocketAddress) sslSocket.getLocalSocketAddress());
    }

    public SSLSocket getSslSocket() {
        return sslSocket;
    }

    @Override
    public void stop() {
        try {
            sslSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            readerThread.interrupt();
        }
    }
}
