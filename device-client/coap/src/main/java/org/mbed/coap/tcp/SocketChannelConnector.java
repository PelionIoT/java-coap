/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.mbed.coap.transport.TransportConnectorTask;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements transport connector for single socket channel. It requires before
 * hand connected socket channel.
 *
 * @author szymon
 */
public class SocketChannelConnector implements TransportConnectorTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelConnector.class);
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private final ByteBuffer buffer;
    private final int maxMessageSize;
    private TransportReceiver udpReceiver;
    private InetSocketAddress socketAddress;
    private SocketChannel socketChannel;

    public SocketChannelConnector() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Construct with buffer size that will be defining maximum message size,
     * both sending and receiving.
     *
     * @param bufferSize in bytes
     */
    public SocketChannelConnector(int bufferSize) {
        buffer = ByteBuffer.allocate(bufferSize);
        this.maxMessageSize = bufferSize;
    }

    /**
     * Sets socket channel that must be connected to remote address and
     * configured as blocking.
     */
    public void setSocketChannel(SocketChannel socketChannel) {
        if (!socketChannel.isBlocking()) {
            throw new IllegalStateException("Socket must be configure as blocking");
        }
        if (!socketChannel.isConnected()) {
            throw new IllegalStateException("Socket must be connected");
        }
        synchronized (this) {
            try {
                this.socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            } catch (IOException ex) {
                throw new IllegalStateException("Could not get remote address", ex);
            }
            this.socketChannel = socketChannel;
            this.notifyAll();
        }
    }

    @Override
    public void start(TransportReceiver udpReceiver) throws IOException {
        this.udpReceiver = udpReceiver;
    }

    @Override
    public synchronized void stop() {
        socketAddress = null;
        socketChannel = null;
        this.notifyAll();
    }

    private synchronized SocketChannel socketChannel() {
        return socketChannel;
    }

    @Override
    public synchronized void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        if (socketAddress == null) {
            throw new IOException("No socket channel available");
        }
        if (len > maxMessageSize) {
            throw new IOException("Too long message.");
        }
        if (destinationAddress.equals(socketAddress)) {
            ByteBuffer buff = ByteBuffer.allocate(4 + len);
            buff.putInt(len);
            buff.put(data, 0, len);
            buff.position(0);
            int bytesSent = socketChannel.write(buff);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Client sent " + bytesSent + " bytes");
            }
        } else {
            throw new IOException("No connection for destination address: " + destinationAddress);
        }
    }

    @Override
    public synchronized InetSocketAddress getLocalSocketAddress() {
        try {
            return (InetSocketAddress) socketChannel.getLocalAddress();
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public void performReceive() {
        SocketChannel sockChnl;
        synchronized (this) {
            if (socketAddress == null) {
                try {
                    LOGGER.trace("No socket to read from, waiting to set socket");
                    this.wait(10000);
                    if (socketAddress == null) {
                        return;
                    }
                } catch (InterruptedException ex) {
                    LOGGER.warn(ex.getMessage());
                }
            }
            sockChnl = socketChannel;
        }
        buffer.clear();
        try {
            if (sockChnl.read(buffer) > 0) {
                int len = buffer.getInt(0);
                if (len > maxMessageSize) {
                    LOGGER.warn("Received too long message: " + len + " , corrupted connection or misbehaving sender? Closing socket.");
                    socketChannel().close();
                    stop();
                }
                ByteBuffer readBuffer = ByteBuffer.allocate(len);
                readBuffer.put(buffer.array(), 4, len);
                udpReceiver.onReceive((InetSocketAddress) sockChnl.getRemoteAddress(), readBuffer, null);
            }

        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
            try {
                // flood stopper
                Thread.sleep(1000);
            } catch (Exception e) {
                LOGGER.error("Sleep interrupted ", e);
            }
        }
    }
}
