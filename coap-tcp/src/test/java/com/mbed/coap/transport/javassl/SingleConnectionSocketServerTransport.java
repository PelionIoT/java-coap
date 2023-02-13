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

import static com.mbed.coap.utils.FutureHelpers.wrapExceptions;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapTcpPacketSerializer;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapTcpListener;
import com.mbed.coap.transport.CoapTcpTransport;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SingleConnectionSocketServerTransport extends BlockingCoapTransport implements CoapTcpTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleConnectionSSLSocketServerTransport.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    final ServerSocket serverSocket;
    private OutputStream outputStream;
    private Socket socket;
    private CoapTcpListener listener;

    SingleConnectionSocketServerTransport(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public SingleConnectionSocketServerTransport(int port) throws IOException {
        this(new ServerSocket(port));
    }


    @Override
    public CompletableFuture<CoapPacket> receive() {
        return CompletableFuture.supplyAsync(() -> wrapExceptions(this::read), executor)
                .thenCompose(it -> (it == null) ? receive() : completedFuture(it));
    }

    private void connect() {
        try {
            LOGGER.debug("SSLSocketServer is listening on " + serverSocket.getLocalSocketAddress());
            socket = serverSocket.accept();

            //connected with client
            synchronized (this) {
                outputStream = new BufferedOutputStream(socket.getOutputStream());
            }
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

            listener.onConnected(remoteSocketAddress);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CoapPacket read() throws IOException, CoapException {
        if (!socket.isClosed()) {
            try {
                final CoapPacket coapPacket = CoapTcpPacketSerializer.deserialize(((InetSocketAddress) socket.getRemoteSocketAddress()), socket.getInputStream());
                return coapPacket;
            } catch (EOFException e) {
                closeSocket();
                throw e;
            } catch (Exception e) {
                if ("Connection reset".equals(e.getMessage())) {
                    closeSocket();
                }
                if (e.getCause() != null && e.getCause() instanceof IOException) {
                    if (e.getCause().getMessage().startsWith("Socket is closed")) {
                        LOGGER.warn(e.getCause().getMessage());
                    }
                }
                throw e;
            }
        } else {
            listener.onDisconnected(((InetSocketAddress) socket.getRemoteSocketAddress()));
            throw new CompletionException(new IOException("Socket is closed"));
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setListener(CoapTcpListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        executor.execute(this::connect);
    }

    @Override
    public void stop() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();
    }

    @Override
    public synchronized void sendPacket0(CoapPacket coapPacket) throws CoapException, IOException {
        CoapTcpPacketSerializer.writeTo(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

}
