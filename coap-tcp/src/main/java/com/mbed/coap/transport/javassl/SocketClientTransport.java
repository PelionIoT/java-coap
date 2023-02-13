/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapTcpPacketSerializer;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapTcpListener;
import com.mbed.coap.transport.CoapTcpTransport;
import com.mbed.coap.utils.ExecutorHelpers;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import javax.net.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketClientTransport extends BlockingCoapTransport implements CoapTcpTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSocketClientTransport.class);

    protected final InetSocketAddress destination;
    protected OutputStream outputStream;
    protected InputStream inputStream;
    protected Socket socket;
    protected CoapTcpListener listener;
    private final ExecutorService readingWorker;
    protected final SocketFactory socketFactory;
    private final boolean autoReconnect;
    private volatile boolean isRunning;

    public SocketClientTransport(InetSocketAddress destination, SocketFactory socketFactory, boolean autoReconnect) {
        this(destination, socketFactory, autoReconnect, ExecutorHelpers.newSingleThreadExecutor("client-reader"));
    }

    public SocketClientTransport(InetSocketAddress destination, SocketFactory socketFactory, boolean autoReconnect, ExecutorService readingWorker) {
        this.destination = destination;
        this.socketFactory = socketFactory;
        this.autoReconnect = autoReconnect;
        this.readingWorker = readingWorker;
    }

    @Override
    public void start() throws IOException {
        isRunning = true;
        connect();
    }

    @Override
    public void setListener(CoapTcpListener listener) {
        this.listener = listener;
    }

    protected void connect() throws IOException {
        socket = socketFactory.createSocket(destination.getAddress(), destination.getPort());

        synchronized (this) {
            outputStream = new BufferedOutputStream(socket.getOutputStream());
        }
        inputStream = new BufferedInputStream(socket.getInputStream(), 2048);

        listener.onConnected((InetSocketAddress) socket.getRemoteSocketAddress());
    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return supplyAsync(this::read, readingWorker)
                .thenCompose(it -> (it == null) ? receive() : completedFuture(it));
    }


    private CoapPacket read() {
        try {
            if (socket.isClosed() && autoReconnect && isRunning) {
                waitBeforeReconnection();
                LOGGER.debug("reconnecting to " + destination);
                connect();
            }

            if (!socket.isClosed()) {
                try {
                    return CoapTcpPacketSerializer.deserialize((InetSocketAddress) socket.getRemoteSocketAddress(), inputStream);
                } catch (CoapException e) {
                    if (e.getCause() != null && e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    LOGGER.warn("Closing socket connection, due to parsing error: " + e.getMessage());
                    socket.close();
                } catch (EOFException ex) {
                    socket.close();
                }
            }
        } catch (SocketTimeoutException ex) {
            return null;
        } catch (Exception ex) {
            if (!(ex.getMessage() != null && ex.getMessage().startsWith("Socket closed"))) {
                LOGGER.error(ex.toString());
            }
        }
        if (socket.isClosed()) {
            listener.onDisconnected(destination);
        }

        if (!autoReconnect && socket.isClosed()) {
            throw new CompletionException(new IOException("Socket closed"));
        } else {
            return null;
        }
    }

    protected void waitBeforeReconnection() throws InterruptedException {
        Thread.sleep(100);
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket) throws CoapException, IOException {
        InetSocketAddress adr = coapPacket.getRemoteAddress();
        if (!adr.equals(this.destination)) {
            throw new IllegalStateException("No connection with: " + adr);
        }
        synchronized (this) {
            CoapTcpPacketSerializer.writeTo(outputStream, coapPacket);
            outputStream.flush();
        }
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    @Override
    public void stop() {
        isRunning = false;
        try {
            listener.onDisconnected(destination);
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            readingWorker.shutdown();
        }
    }
}
