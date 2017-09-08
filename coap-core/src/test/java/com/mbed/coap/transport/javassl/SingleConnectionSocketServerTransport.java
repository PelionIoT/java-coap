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
import com.mbed.coap.packet.CoapTcpPacketSerializer;
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
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
public class SingleConnectionSocketServerTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleConnectionSSLSocketServerTransport.class);

    private Thread serverThread;
    final ServerSocket serverSocket;
    private OutputStream outputStream;
    private final boolean isTcpCoapPacket;

    SingleConnectionSocketServerTransport(ServerSocket serverSocket, boolean isTcpCoapPacket) throws IOException {
        this.serverSocket = serverSocket;
        this.isTcpCoapPacket = isTcpCoapPacket;
    }

    public SingleConnectionSocketServerTransport(int port, boolean isTcpCoapPacket) throws IOException {
        this(new ServerSocket(port), isTcpCoapPacket);
    }


    private void serverLoop(CoapReceiverForTcp coapReceiver) {
        try {
            LOGGER.debug("SSLSocketServer is listening on " + serverSocket.getLocalSocketAddress());
            Socket socket = serverSocket.accept();

            //connected with client
            synchronized (this) {
                outputStream = new BufferedOutputStream(socket.getOutputStream());
            }
            final InputStream inputStream = new BufferedInputStream(socket.getInputStream());
            InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

            coapReceiver.onConnected(remoteSocketAddress);

            while (!socket.isClosed()) {
                try {
                    final CoapPacket coapPacket = deserialize(inputStream, remoteSocketAddress);
                    coapReceiver.handle(coapPacket, TransportContext.NULL);
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
    public void start(CoapReceiver coapReceiver) throws IOException {
        serverThread = new Thread(() -> serverLoop(CoapReceiverForTcp.from(coapReceiver)), "sslsocket-server");
        serverThread.start();
    }

    @Override
    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        serverThread.interrupt();
    }

    @Override
    public synchronized void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        serialize(coapPacket);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    private CoapPacket deserialize(InputStream inputStream, InetSocketAddress remoteSocketAddress) throws CoapException, IOException {
        if (isTcpCoapPacket) {
            return CoapTcpPacketSerializer.deserialize(remoteSocketAddress, inputStream);
        } else {
            return CoapPacket.deserialize(remoteSocketAddress, inputStream);
        }
    }


    private void serialize(CoapPacket coapPacket) throws CoapException, IOException {
        if (isTcpCoapPacket) {
            CoapTcpPacketSerializer.writeTo(outputStream, coapPacket);
        } else {
            coapPacket.writeTo(outputStream);
        }
    }
}