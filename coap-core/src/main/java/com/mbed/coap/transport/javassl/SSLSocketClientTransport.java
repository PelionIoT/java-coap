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

import com.mbed.coap.transport.CoapReceiver;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Supplier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLSocketClientTransport extends SocketClientTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSocketClientTransport.class);

    public SSLSocketClientTransport(Supplier<InetSocketAddress> destination, SSLSocketFactory socketFactory, CoapSerializer serializer, boolean autoReconnect) {
        super(destination, socketFactory, serializer, autoReconnect);
    }

    public SSLSocketClientTransport(InetSocketAddress destination, SSLSocketFactory socketFactory, CoapSerializer serializer, boolean autoReconnect) {
        this(() -> destination, socketFactory, serializer, autoReconnect);
    }

    @Override
    protected void connect(CoapReceiver coapReceiver) throws IOException {
        InetSocketAddress destination = destinationSupplier.get();

        SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(destination.getAddress(), destination.getPort());

        sslSocket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                    try {
                        LOGGER.debug("Connected [" + handshakeCompletedEvent.getSource() + ", " + sslSocket.getSession().getPeerCertificateChain()[0].getSubjectDN() + "]");
                    } catch (SSLPeerUnverifiedException e) {
                        LOGGER.warn(e.getMessage(), e);
                    }
            coapReceiver.onConnected((InetSocketAddress) socket.getRemoteSocketAddress());
                }
        );
        sslSocket.startHandshake();

        this.socket = sslSocket;

        synchronized (this) {
            outputStream = new BufferedOutputStream(socket.getOutputStream());
        }
        inputStream = new BufferedInputStream(socket.getInputStream(), 1024);
    }

    public SSLSocket getSslSocket() {
        return ((SSLSocket) socket);
    }

}
