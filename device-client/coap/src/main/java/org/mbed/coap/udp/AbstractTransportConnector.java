/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.mbed.coap.transport.TransportConnectorTask;
import org.mbed.coap.transport.TransportReceiver;

/**
 *
 * @author szymon
 */
public abstract class AbstractTransportConnector implements TransportConnectorTask {

    private InetSocketAddress bindSocket;
    private TransportReceiver transReceiver;
    protected boolean isRunning;
    protected int bufferSize = DEFAULT_BUFFER_SIZE;
    private static final int DEFAULT_BUFFER_SIZE = 1080;
    private final ThreadLocal<ByteBuffer> buffer = new ThreadLocal<>();

    public AbstractTransportConnector(InetSocketAddress bindSocket) {
        this.bindSocket = bindSocket;
    }

    public AbstractTransportConnector(int port) {
        this(new InetSocketAddress(port));
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    protected abstract void initialize() throws IOException;

    protected void setTransportReceiver(TransportReceiver transReceiver) {
        if (transReceiver == null) {
            throw new NullPointerException();
        }
        this.transReceiver = transReceiver;
    }

    @Override
    public void start(TransportReceiver transHandler) throws IOException {
        setTransportReceiver(transHandler);
        initialize();
        isRunning = true;

    }

    protected final InetSocketAddress getBindSocket() {
        return bindSocket;
    }

    @Override
    public void stop() {
        isRunning = false;

    }

    /**
     * Read incoming message from socket. Returns sender address and fills
     * buffer with message payload
     *
     * @param transReceiver buffer for message payload
     * @return sender address
     */
    protected abstract boolean receive(TransportReceiver transReceiver);

    @Override
    public void performReceive() {
        while (true) {
            if (!receive(transReceiver)) {
                break;
            }
        }
    }

    protected ByteBuffer getBuffer() {

        if (buffer.get() == null) {
            buffer.set(ByteBuffer.allocate(bufferSize));
        }
        return (ByteBuffer) buffer.get().clear();
    }

    public static byte[] createCopyOfPacketData(ByteBuffer buffer, int len) {
        byte[] packetData = new byte[len];
        System.arraycopy(buffer.array(), 0, packetData, 0, packetData.length);
        return packetData;
    }


    protected final boolean isRunning() {
        return isRunning;
    }

}
