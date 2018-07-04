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
package com.mbed.coap.transport.javassl;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapReceiverForTcp;
import com.mbed.coap.transport.TransportContext;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public class SocketClientTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSocketClientTransport.class);

    protected final InetSocketAddress destination;
    private OutputStream outputStream;
    private InputStream inputStream;
    protected Socket socket;
    private Thread readerThread;
    protected final SocketFactory socketFactory;
    private final CoapSerializer serializer;

    public SocketClientTransport(InetSocketAddress destination, SocketFactory socketFactory, CoapSerializer serializer) {
        this.destination = destination;
        this.socketFactory = socketFactory;
        this.serializer = serializer;
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        initSocket();

        synchronized (this) {
            outputStream = new BufferedOutputStream(socket.getOutputStream());
        }
        inputStream = new BufferedInputStream(socket.getInputStream(), 1024);

        readerThread = new Thread(() -> loopReading(CoapReceiverForTcp.from(coapReceiver)), "tls-client-read");
        readerThread.start();
    }

    protected void initSocket() throws IOException {
        socket = socketFactory.createSocket(destination.getAddress(), destination.getPort());
    }

    private void loopReading(CoapReceiverForTcp coapReceiver) {
        try {
            coapReceiver.onConnected((InetSocketAddress) socket.getRemoteSocketAddress());
            while (!socket.isClosed()) {
                try {
                    final CoapPacket coapPacket = serializer.deserialize(inputStream, ((InetSocketAddress) socket.getRemoteSocketAddress()));
                    coapReceiver.handle(coapPacket, TransportContext.NULL);
                } catch (CoapException e) {
                    if (e.getCause() != null && e.getCause() instanceof IOException) {
                        throw ((IOException) e.getCause());
                    }
                    LOGGER.warn("Closing socket connection, due to parsing error: " + e.getMessage());
                    socket.close();
                }
            }
        } catch (Exception ex) {
            if (!(ex.getMessage() != null && ex.getMessage().startsWith("Socket closed"))) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
        coapReceiver.onDisconnected(destination);
    }

    @Override
    public synchronized void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        if (!adr.equals(this.destination)) {
            throw new IllegalStateException("No connection with: " + adr);
        }
        serializer.serialize(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return ((InetSocketAddress) socket.getLocalSocketAddress());
    }

    @Override
    public void stop() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            readerThread.interrupt();
        }
    }
}
