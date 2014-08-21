/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import org.mbed.coap.transport.TransportConnectorTask;
import org.mbed.coap.transport.TransportReceiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

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
    private final ThreadLocal<ByteBuffer> buffer = new ThreadLocal<ByteBuffer>();

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
        //ByteBuffer buf = getBuffer();
        while (receive(transReceiver)) {
        }
//        while (adr != null) {
//            udpReceiver.onReceive(createUnresolvedInetSocketAddress(adr), buf, getAndClearTransportContext());
//            buf = getBuffer();
//            adr = receive(buf);
//        }
    }

//    protected static InetSocketAddress createUnresolvedInetSocketAddress(SocketAddress socAddress) {
//        InetSocketAddress addr = (InetSocketAddress) socAddress;
//        try {
//            return new InetSocketAddress(InetAddress.getByAddress(addr.getAddress().getAddress()), addr.getPort());
//        } catch (UnknownHostException ex) {
//            throw new RuntimeException(ex);
//        }
//    }

    protected ByteBuffer getBuffer() {

        if (buffer.get() == null) {
            buffer.set(ByteBuffer.allocate(bufferSize));
        }
        return (ByteBuffer) buffer.get().clear();
    }

    protected final boolean isRunning() {
        return isRunning;
    }

}
