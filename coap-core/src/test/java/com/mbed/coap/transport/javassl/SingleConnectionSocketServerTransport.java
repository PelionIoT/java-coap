/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SingleConnectionSocketServerTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleConnectionSSLSocketServerTransport.class);

    private Thread serverThread;
    final ServerSocket serverSocket;
    private OutputStream outputStream;
    private final CoapSerializer serializer;
    private Socket socket;

    SingleConnectionSocketServerTransport(ServerSocket serverSocket, CoapSerializer serializer) {
        this.serverSocket = serverSocket;
        this.serializer = serializer;
    }

    public SingleConnectionSocketServerTransport(int port, CoapSerializer serializer) throws IOException {
        this(new ServerSocket(port), serializer);
    }


    private void serverLoop(CoapReceiver coapReceiver) {
        try {
            LOGGER.debug("SSLSocketServer is listening on " + serverSocket.getLocalSocketAddress());
            socket = serverSocket.accept();

            //connected with client
            synchronized (this) {
                outputStream = new BufferedOutputStream(socket.getOutputStream());
            }
            final InputStream inputStream = new BufferedInputStream(socket.getInputStream());
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

            coapReceiver.onConnected(remoteSocketAddress);

            while (!socket.isClosed()) {
                try {
                    final CoapPacket coapPacket = serializer.deserialize(inputStream, remoteSocketAddress);
                    coapReceiver.handle(coapPacket);
                } catch (EOFException e) {
                    socket.close();
                } catch (Exception e) {
                    if ("Connection reset".equals(e.getMessage())) {
                        socket.close();
                    }
                    if (e.getCause() != null && e.getCause() instanceof IOException) {
                        if (e.getCause().getMessage().startsWith("Socket is closed")) {
                            LOGGER.warn(e.getCause().getMessage());
                        } else {
                            throw ((IOException) e.getCause());
                        }
                    }
                    //LOGGER.warn("Closing socket connection, due to parsing error: " + e.getMessage());
                    //sslSocket.close();
                }
            }
            coapReceiver.onDisconnected(remoteSocketAddress);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(CoapReceiver coapReceiver) {
        serverThread = new Thread(() -> serverLoop(coapReceiver), "sslsocket-server");
        serverThread.start();
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
        serverThread.interrupt();
    }

    @Override
    public synchronized void sendPacket0(CoapPacket coapPacket) throws CoapException, IOException {
        serializer.serialize(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

}