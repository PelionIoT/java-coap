/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.Executor;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.transport.CoapReceiver;
import org.mbed.coap.transport.CoapTransport;
import org.mbed.coap.transport.TransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Datagram transport based on DatagramSocket. Not thread-save.
 *
 * @author szymon
 */
public class DatagramSocketTransport implements CoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramSocketTransport.class.getName());
    private final InetSocketAddress bindSocket;
    private final Executor receivedMessageWorker;
    private DatagramSocket socket;
    private int socketBufferSize = -1;
    protected boolean reuseAddress;
    private Thread readerThread;
    private final boolean initReaderThread;

    protected DatagramSocketTransport(InetSocketAddress bindSocket, Executor receivedMessageWorker, boolean initReaderThread) {
        this.bindSocket = bindSocket;
        this.receivedMessageWorker = receivedMessageWorker;
        this.initReaderThread = initReaderThread;
    }

    public DatagramSocketTransport(InetSocketAddress bindSocket, Executor receivedMessageWorker) {
        this(bindSocket, receivedMessageWorker, true);
    }

    public DatagramSocketTransport(int localPort, Executor receivedMessageWorker) {
        this(new InetSocketAddress(localPort), receivedMessageWorker);
    }

    public DatagramSocketTransport(int localPort) {
        this(localPort, Runnable::run);
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
    public void start(CoapReceiver coapReceiver) throws IOException {
        socket = createSocket();
        if (socketBufferSize > 0) {
            socket.setReceiveBufferSize(socketBufferSize);
            socket.setSendBufferSize(socketBufferSize);
        }
        socket.setReuseAddress(reuseAddress);
        LOGGER.info("CoAP server binds on " + socket.getLocalSocketAddress());
        if (socketBufferSize > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("DatagramSocket [receiveBuffer: " + socket.getReceiveBufferSize() + ", sendBuffer: " + socket.getSendBufferSize() + "]");
        }

        readerThread = new Thread(() -> readingLoop(coapReceiver), "multicast-reader");
        if (initReaderThread) {
            readerThread.start();
        }
    }

    private void readingLoop(CoapReceiver coapReceiver) {
        byte[] readBuffer = new byte[2048];

        try {
            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(readBuffer, readBuffer.length);
                socket.receive(datagramPacket);

                final InetSocketAddress remoteAddress = (InetSocketAddress) datagramPacket.getSocketAddress();
                final byte[] datagramData = new byte[datagramPacket.getLength()];
                System.arraycopy(readBuffer, 0, datagramData, 0, datagramPacket.getLength());

                receivedMessageWorker.execute(() -> {
                    try {
                        final CoapPacket coapPacket = CoapPacket.read(remoteAddress, datagramData, datagramData.length);
                        coapReceiver.handle(coapPacket, TransportContext.NULL);
                    } catch (CoapException e) {
                        LOGGER.warn(e.getMessage());
                    }
                });
            }
        } catch (IOException ex) {
            if (!ex.getMessage().startsWith("Socket closed")) {
                LOGGER.warn(ex.getMessage(), ex);
            }
        }
    }

    protected DatagramSocket createSocket() throws SocketException {
        return new QoSDatagramSocket(bindSocket);
    }

    @Override
    public void stop() {
        if (socket != null) {
            readerThread.interrupt();
            socket.close();
        }
    }

    @Override
    public void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext transContext) throws CoapException, IOException {
        if (socket == null) {
            throw new IllegalStateException();
        }
        byte[] data = coapPacket.toByteArray();

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, adr);

        if (transContext != null) {
            Integer tc = TrafficClassTransportContext.readFrom(transContext);
            if (tc != null && tc > 0) {
                synchronized (this) {
                    socket.setTrafficClass(tc);
                    socket.send(datagramPacket);
                    socket.setTrafficClass(0);
                }
                return;
            }
        }
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
