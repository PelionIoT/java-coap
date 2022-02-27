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
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportExecutors;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Datagram transport based on DatagramSocket. Not thread-save.
 */
public class DatagramSocketTransport extends BlockingCoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramSocketTransport.class.getName());
    private final InetSocketAddress bindAddress;
    protected BlockingSocket socket;
    private final Executor readingWorker;

    public DatagramSocketTransport(InetSocketAddress bindAddress) {
        this(null, bindAddress, null);
    }

    public DatagramSocketTransport(InetSocketAddress bindAddress, Executor readingWorker) {
        this(null, bindAddress, readingWorker);
    }

    public DatagramSocketTransport(BlockingSocket datagramSocket, Executor readingWorker) {
        this(datagramSocket, datagramSocket.getBoundAddress(), readingWorker);
    }

    private DatagramSocketTransport(BlockingSocket datagramSocket, InetSocketAddress bindAddress, Executor readingWorker) {
        this.socket = datagramSocket;
        this.bindAddress = bindAddress;
        if (readingWorker != null) {
            this.readingWorker = readingWorker;
        } else {
            this.readingWorker = TransportExecutors.newWorker("udp-reader");
        }
    }

    public DatagramSocketTransport(int localPort) {
        this(new InetSocketAddress(localPort));
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        if (!socketCreated()) {
            createSocket();
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
            if (!ex.getMessage().startsWith("Socket closed")&&!ex.getMessage().startsWith("socket closed")) {
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

    protected void createSocket() throws SocketException {
        socket = new DatagramSocketAdapter(bindAddress);
    }

    @Override
    public void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        TransportExecutors.shutdown(readingWorker);
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext transContext) throws CoapException, IOException {
        if (!socketCreated()) {
            throw new IllegalStateException();
        }
        byte[] data = coapPacket.toByteArray();

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, adr);

        socket.send(datagramPacket);
    }

    protected boolean socketCreated() {
        return (socket != null);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return getSocket().getBoundAddress();
    }

    public BlockingSocket getSocket() {
        return socket;
    }

}
