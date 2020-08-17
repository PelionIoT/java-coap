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
package com.mbed.coap.transport.udp;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.WithReconnectionSupport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportExecutors;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Datagram transport based on DatagramSocket. Not thread-save.
 *
 * @author szymon
 */
public class DatagramSocketTransport extends BlockingCoapTransport implements WithReconnectionSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramSocketTransport.class.getName());
    private final Supplier<InetSocketAddress> bindAddressSupplier;
    private DatagramSocket socket;
    private int socketBufferSize = -1;
    protected boolean reuseAddress;
    private final Executor readingWorker;

    public DatagramSocketTransport(InetSocketAddress bindAddress) {
        this(null, bindAddress, null);
    }

    public DatagramSocketTransport(InetSocketAddress bindAddress, Executor readingWorker) {
        this(null, bindAddress, readingWorker);
    }

    public DatagramSocketTransport(DatagramSocket datagramSocket, Executor readingWorker) {
        this(datagramSocket, ((InetSocketAddress) datagramSocket.getLocalSocketAddress()), readingWorker);
    }

    public DatagramSocketTransport(Supplier<InetSocketAddress> bindAddress, Executor readingWorker) {
        this(null, bindAddress, readingWorker);
    }

    private DatagramSocketTransport(DatagramSocket datagramSocket, Supplier<InetSocketAddress> bindAddress, Executor readingWorker) {
        this.socket = datagramSocket;
        this.bindAddressSupplier = bindAddress;
        if (readingWorker != null) {
            this.readingWorker = readingWorker;
        } else {
            this.readingWorker = TransportExecutors.newWorker("udp-reader");
        }
    }

    private DatagramSocketTransport(DatagramSocket datagramSocket, InetSocketAddress bindAddress, Executor readingWorker) {
        this(datagramSocket, ()->bindAddress, readingWorker);
    }

    public DatagramSocketTransport(int localPort) {
        this(new InetSocketAddress(localPort));
    }

    public void setSocketBufferSize(int socketBufferSize) {
        if (socket != null) {
            throw new IllegalStateException();
        }
        this.socketBufferSize = socketBufferSize;
    }

    public void setReuseAddress(boolean reuseAddress) {
        if (socket != null) {
            throw new IllegalStateException();
        }
        this.reuseAddress = reuseAddress;
    }

    @Override
    public void reconnect() throws SocketException {
        if (socket != null) {
            socket.close();
        }
        socket = createSocket();
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        if (socket == null) {
            socket = createSocket();
        }

        if (socketBufferSize > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("DatagramSocket [receiveBuffer: " + socket.getReceiveBufferSize() + ", sendBuffer: " + socket.getSendBufferSize() + "]");
        }

        TransportExecutors.loop(readingWorker, () -> readingLoop(coapReceiver));
    }

    protected boolean readingLoop(CoapReceiver coapReceiver) {
        byte[] readBuffer = new byte[2048];

        try {
            DatagramPacket datagramPacket = new DatagramPacket(readBuffer, readBuffer.length);
            socket.receive(datagramPacket);

            receive(coapReceiver, datagramPacket);
            return true;
        } catch (SocketTimeoutException ex) {
            return true;
        } catch (IOException ex) {
            if (!ex.getMessage().startsWith("Socket closed")) {
                LOGGER.warn(ex.getMessage(), ex);
            }
        } catch (Exception ex) {
            LOGGER.warn(ex.getMessage());
        }
        return false;
    }

    protected void receive(CoapReceiver coapReceiver, DatagramPacket datagramPacket) {
        try {
            final CoapPacket coapPacket = CoapPacket.read((InetSocketAddress) datagramPacket.getSocketAddress(), datagramPacket.getData(), datagramPacket.getLength());
            coapReceiver.handle(coapPacket, TransportContext.NULL);
        } catch (CoapException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    protected DatagramSocket createSocket() throws SocketException {
        DatagramSocket datagramSocket = new DatagramSocket(bindAddressSupplier.get());
        datagramSocket.setSoTimeout(200);
        if (socketBufferSize > 0) {
            datagramSocket.setReceiveBufferSize(socketBufferSize);
            datagramSocket.setSendBufferSize(socketBufferSize);
        }
        datagramSocket.setReuseAddress(reuseAddress);
        LOGGER.info("CoAP server binds on " + datagramSocket.getLocalSocketAddress());
        return datagramSocket;
    }

    @Override
    public void stop() {
        if (socket != null) {
            socket.close();
        }
        TransportExecutors.shutdown(readingWorker);
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext transContext) throws CoapException, IOException {
        if (socket == null) {
            throw new IllegalStateException();
        }
        byte[] data = coapPacket.toByteArray();

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, adr);

        socket.send(datagramPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    protected DatagramSocket getSocket() {
        return socket;
    }

}
