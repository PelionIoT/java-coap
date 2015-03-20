/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.transport.TransportContext;

/**
 * Datagram transport based on DatagramSocket. Not thread-save.
 *
 * @author szymon
 */
public class DatagramSocketTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = Logger.getLogger(DatagramSocketTransport.class.getName());
    private DatagramSocket socket;
    private int socketBufferSize = -1;
    protected boolean reuseAddress;

    public DatagramSocketTransport(InetSocketAddress bindSocket) {
        super(bindSocket);
    }

    public DatagramSocketTransport(int localPort) {
        super(localPort);
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
    protected void initialize() throws IOException {
        socket = createSocket();
        if (socketBufferSize > 0) {
            socket.setReceiveBufferSize(socketBufferSize);
            socket.setSendBufferSize(socketBufferSize);
        }
        socket.setReuseAddress(reuseAddress);
        LOGGER.info("CoAP server binds on " + socket.getLocalSocketAddress());
        if (socketBufferSize > 0 && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("DatagramSocket [receiveBuffer: " + socket.getReceiveBufferSize() + ", sendBuffer: " + socket.getSendBufferSize() + "]");
        }
    }

    protected DatagramSocket createSocket() throws SocketException {
        return new QoSDatagramSocket(getBindSocket());
    }

    @Override
    public void stop() {
        super.stop();
        socket.close();
    }

    @Override
    public boolean performReceive() {
        ByteBuffer buffer = getBuffer();
        DatagramPacket datagramPacket = new DatagramPacket(buffer.array(), buffer.limit());
        try {
            socket.receive(datagramPacket);

            byte[] packetData = createCopyOfPacketData(buffer, datagramPacket.getLength());

            transReceiver.onReceive((InetSocketAddress) datagramPacket.getSocketAddress(), packetData, TransportContext.NULL);
            return true;
        } catch (IOException ex) {
            if (!isRunning() && "socket closed".equalsIgnoreCase(ex.getMessage())) {
                LOGGER.fine("DatagramSocket was closed.");
            } else {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return false;
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        if (socket == null) {
            throw new IllegalStateException();
        }
        DatagramPacket datagramPacket = new DatagramPacket(data, len, destinationAddress);
        if (transContext != null) {
            Integer tc = transContext.getTrafficClass();
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
