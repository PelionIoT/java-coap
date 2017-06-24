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
package com.mbed.coap.transport.udp;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public final class MulticastSocketTransport extends BlockingCoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastSocketTransport.class.getName());
    private final InetSocketAddress bindSocket;
    private final Executor receivedMessageWorker;
    private MulticastSocket mcastSocket;
    private final InetAddress mcastGroup;
    private Thread readerThread;

    public final static String MCAST_LINKLOCAL_ALLNODES = "FF02::1";    //NOPMD
    public final static String MCAST_NODELOCAL_ALLNODES = "FF01::1";    //NOPMD

    public MulticastSocketTransport(InetSocketAddress bindSocket, String multicastGroup, Executor receivedMessageWorker) throws UnknownHostException {
        this.bindSocket = bindSocket;
        this.receivedMessageWorker = receivedMessageWorker;
        mcastGroup = InetAddress.getByName(multicastGroup);
    }

    @Override
    public void stop() {
        mcastSocket.close();
        readerThread.interrupt();
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        mcastSocket = new MulticastSocket(bindSocket);
        mcastSocket.joinGroup(mcastGroup);
        LOGGER.debug("CoAP server binds on multicast " + mcastSocket.getLocalSocketAddress());

        readerThread = new Thread(() -> readingLoop(coapReceiver), "multicast-reader");
        readerThread.start();
    }

    private void readingLoop(CoapReceiver coapReceiver) {
        byte[] readBuffer = new byte[2048];

        try {
            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(readBuffer, readBuffer.length);
                mcastSocket.receive(datagramPacket);
                InetSocketAddress adr = (InetSocketAddress) datagramPacket.getSocketAddress();
                if (LOGGER.isDebugEnabled() && adr.getAddress().isMulticastAddress()) {
                    LOGGER.debug("Received multicast message from: " + datagramPacket.getSocketAddress());
                }

                try {
                    final CoapPacket coapPacket = CoapPacket.read(adr, datagramPacket.getData(), datagramPacket.getLength());
                    receivedMessageWorker.execute(() -> coapReceiver.handle(coapPacket, TransportContext.NULL));
                } catch (CoapException e) {
                    LOGGER.warn(e.getMessage());
                }
            }
        } catch (IOException ex) {
            if (!ex.getMessage().startsWith("Socket closed")) {
                LOGGER.warn(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        coapPacket.writeTo(baos);
        byte[] data = baos.toByteArray();
        baos.close();

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, adr);
        mcastSocket.send(datagramPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) mcastSocket.getLocalSocketAddress();
    }

}
