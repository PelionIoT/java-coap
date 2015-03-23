/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author szymon
 */
public abstract class AbstractTransportConnector implements TransportConnector {

    private static final Logger LOGGER = Logger.getLogger(AbstractTransportConnector.class.getName());
    private InetSocketAddress bindSocket;
    protected TransportReceiver transReceiver;
    protected boolean isRunning;
    protected int bufferSize = DEFAULT_BUFFER_SIZE;
    private static final int DEFAULT_BUFFER_SIZE = 1080;
    private final ThreadLocal<ByteBuffer> buffer = new ThreadLocal<>();
    private Thread readerThread;
    private final boolean initReaderThread;

    public AbstractTransportConnector(InetSocketAddress bindSocket, boolean initReaderThread) {
        this.bindSocket = bindSocket;
        this.initReaderThread = initReaderThread;
    }

    public AbstractTransportConnector(InetSocketAddress bindSocket) {
        this(bindSocket, true);
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
    public void start(TransportReceiver transportReceiver) throws IOException {
        setTransportReceiver(transportReceiver);
        initialize();
        isRunning = true;

        if (initReaderThread) {
            //start reading thread
            readerThread = new Thread(this::receiveWhileRunning);
            readerThread.start();
        }
    }

    private void receiveWhileRunning() {
        while (isRunning) {
            try {
                performReceive();
            } catch (Throwable ex) {    //NOPMD  bug in executor when any Exception is thrown
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }


    protected final InetSocketAddress getBindSocket() {
        return bindSocket;
    }

    @Override
    public void stop() {
        isRunning = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    /**
     * Calls transport reading method to receive new incoming message. This method may block if transport was configured
     * to use blocking mode.
     *
     * @return true if new message was received otherwise false
     */
    public abstract boolean performReceive();

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
