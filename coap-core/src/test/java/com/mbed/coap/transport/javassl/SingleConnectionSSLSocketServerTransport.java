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
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleConnectionSSLSocketServerTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleConnectionSSLSocketServerTransport.class);

    private Thread serverThread;
    private final SSLServerSocket serverSocket;
    private OutputStream outputStream;

    public SingleConnectionSSLSocketServerTransport(SSLContext sslContext, int port) throws IOException {
        serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port);
        serverSocket.setNeedClientAuth(true);
    }

    private void serverLoop(CoapReceiver coapReceiver) {
        try {
            LOGGER.debug("SSLSocketServer is listening on " + serverSocket.getLocalSocketAddress());
            SSLSocket sslSocket = (SSLSocket) serverSocket.accept();

            //connected with client
            synchronized (this) {
                outputStream = new BufferedOutputStream(sslSocket.getOutputStream());
            }
            final InputStream inputStream = new BufferedInputStream(sslSocket.getInputStream());

            while (!sslSocket.isClosed()) {
                try {
                    final CoapPacket coapPacket = CoapPacket.deserialize(((InetSocketAddress) sslSocket.getRemoteSocketAddress()), inputStream);
                    coapReceiver.handle(coapPacket, TransportContext.NULL);
                } catch (CoapException e) {
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


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        serverThread = new Thread(() -> serverLoop(coapReceiver), "sslsocket-server");
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
        coapPacket.writeTo(outputStream);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }
}
