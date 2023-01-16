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
package com.mbed.coap.transport.udp;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.utils.ExecutorHelpers;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Datagram transport based on DatagramSocket. Not thread-save.
 */
public class DatagramSocketTransport extends BlockingCoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramSocketTransport.class.getName());
    private final InetSocketAddress bindAddress;
    protected DatagramSocket socket;
    private final ExecutorService readingWorker;

    public DatagramSocketTransport(InetSocketAddress bindAddress) {
        this(null, bindAddress, null);
    }

    private DatagramSocketTransport(DatagramSocket datagramSocket, InetSocketAddress bindAddress, ExecutorService readingWorker) {
        this.socket = datagramSocket;
        this.bindAddress = bindAddress;
        if (readingWorker != null) {
            this.readingWorker = readingWorker;
        } else {
            this.readingWorker = ExecutorHelpers.newSingleThreadExecutor("udp-reader");
        }
    }

    public DatagramSocketTransport(int localPort) {
        this(new InetSocketAddress(localPort));
    }

    @Override
    public void start() throws IOException {
        if (!socketCreated()) {
            createSocket();
        }
    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return supplyAsync(this::blockingReceive, readingWorker)
                .thenCompose(it -> (it == null) ? receive() : completedFuture(it));
    }

    private CoapPacket blockingReceive() {
        byte[] readBuffer = new byte[2048];
        CoapPacket packet = null;
        try {
            DatagramPacket datagramPacket = new DatagramPacket(readBuffer, readBuffer.length);
            socket.receive(datagramPacket);
            packet = CoapPacket.read((InetSocketAddress) datagramPacket.getSocketAddress(), datagramPacket.getData(), datagramPacket.getLength());
        } catch (CoapException e) {
            LOGGER.warn(e.toString(), e);
        } catch (SocketTimeoutException ex) {
            // do nothing
        } catch (IOException e) {
            throw new CompletionException(e);
        }
        return packet;
    }

    protected void createSocket() throws SocketException {
        socket = new DatagramSocket(bindAddress);
    }

    @Override
    public void stop() {
        if (socket != null) {
            socket.close();
        }
        readingWorker.shutdown();
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket) throws CoapException, IOException {
        if (!socketCreated()) {
            throw new IllegalStateException();
        }
        byte[] data = coapPacket.toByteArray();

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, coapPacket.getRemoteAddress());

        socket.send(datagramPacket);
    }

    protected boolean socketCreated() {
        return socket != null;
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

}
