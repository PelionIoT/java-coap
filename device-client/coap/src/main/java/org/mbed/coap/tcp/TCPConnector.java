/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tcp;

import org.mbed.coap.transport.TransportReceiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Kalle Väyrynen
 */
public abstract class TCPConnector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(TCPConnector.class);
    protected final Map<InetSocketAddress, ByteBuffer> oldReadBuffer = new HashMap<InetSocketAddress, ByteBuffer>();
    protected final Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<SocketChannel, List<ByteBuffer>>();
    protected final List<ChangeRequest> changeRequests = new LinkedList<ChangeRequest>();
    protected final Map<InetSocketAddress, SocketChannel> sockets = new HashMap<InetSocketAddress, SocketChannel>();
    protected boolean isRunning;
    protected Selector selector;
    protected TransportReceiver udpReceiver;
    protected final int MAX_LENGTH;
    private static final int DEFAULT_MAX_LENGTH = 1024;
    protected static final int LENGTH_BYTES = 4;

    /**
     * Constructs TCPConnector with DEFAULT_MAX_LENGTH message size
     */
    public TCPConnector() {
        this(DEFAULT_MAX_LENGTH);
    }
    
    /**
     * Constructs TCPConnector with given message size
     * @param maxMessageLength maximum message length in bytes, both read and write
     * uses this.
     */
    public TCPConnector(int maxMessageLength) {
        MAX_LENGTH = maxMessageLength;
    }
    
    public void start(TransportReceiver udpReceiver) throws IOException {
        this.udpReceiver = udpReceiver;
        initialize();
    }

    protected void initialize() throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.debug("TCPConnector initializes in thread " + getThreadName());
        }
        selector = initSelector();
        pendingData.clear();
        changeRequests.clear();
        sockets.clear();
        oldReadBuffer.clear();
        isRunning = true;
        Thread thread = new Thread(this, getThreadName());
        thread.start();
    }

    public void stop() {
        isRunning = false;
        try {
            selector.close();
            for (SocketChannel sc : sockets.values()) {
                sc.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Stopping selector and channel caused error ", e);
        }
        oldReadBuffer.clear();
        pendingData.clear();
        changeRequests.clear();
        sockets.clear();
    }

    abstract Selector initSelector() throws IOException;

    abstract String getThreadName();

    final public void read(SelectionKey key) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket reading");
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        InetSocketAddress socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        ByteBuffer lenBuffer = ByteBuffer.allocate(LENGTH_BYTES);
        ByteBuffer readBuffer = null;
        long numRead;
        try {
            ByteBuffer partialMessage = oldReadBuffer.get(socketAddress);
            if (partialMessage == null) {
                LOGGER.trace("Incoming message does not have old message fragment stored.");
                // new message fragment, just try to read all.
                numRead = socketChannel.read(lenBuffer);
            } else {
                // old fragment has already been read
                LOGGER.trace("Incoming message does have old message fragment stored, continuing filling old buffer, remaining: " + partialMessage.remaining());
                numRead = partialMessage.remaining();
            }

            if (numRead != -1) {
                if (partialMessage == null) {
                    int length = lenBuffer.getInt(0);
                    LOGGER.trace("New msg length " + length);
                    if (length < 1 || length > MAX_LENGTH) {
                        LOGGER.warn("Received message length of " + length + ", which is invalid, closing socket.");
                        key.cancel();
                        socketChannel.close();
                        return;
                    }
                    readBuffer = ByteBuffer.allocate(length);
                    numRead = socketChannel.read(readBuffer);
                } else {
                    readBuffer = ByteBuffer.allocate(partialMessage.remaining());
                    LOGGER.trace("Remaining: " + partialMessage.remaining());
                    numRead = socketChannel.read(readBuffer);
                    LOGGER.trace("Received new fragment: " + numRead);
                    if (numRead == -1) {
                        LOGGER.warn("Received -1 bytes from channel, unexpectedly closed channel? Finalizing selector and clearing buffers.");
                        key.cancel();
                        socketChannel.close();
                        oldReadBuffer.remove(socketAddress);
                        return;
                    }
                    partialMessage.put(readBuffer.array(), 0, (int) numRead);
                    LOGGER.trace("Partial message now remaining: " + partialMessage.remaining());
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Socket reading length read " + numRead + ", should be " + readBuffer.limit() + " clienthash " + this.hashCode());
                }
                if (numRead < readBuffer.limit()) {
                    LOGGER.trace("Not all message was received.");
                    if (partialMessage == null) {
                        LOGGER.trace("Putting readBuffer to oldBuffers, length " + readBuffer.limit());
                        oldReadBuffer.put(socketAddress, readBuffer);
                    } else {
                        LOGGER.trace("Replacing partialMessageBuffer to oldBuffers");
                        oldReadBuffer.put(socketAddress, partialMessage);
                    }
                    return;
                } else {
                    if (partialMessage != null) {
                        readBuffer = partialMessage;
                        oldReadBuffer.remove(socketAddress);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Socket closed while reading, removing key and closing channel");
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            LOGGER.warn(("Client has closed the socket, clean up server key and channel [" + socketChannel.getRemoteAddress() + "]"));
            if (sockets.remove(socketChannel.getRemoteAddress()) == null) {
                LOGGER.trace("Could not find and remove client socket");
            }
            key.channel().close();
            key.cancel();
            return;
        }

        try {
            udpReceiver.onReceive((InetSocketAddress) socketChannel.socket().getRemoteSocketAddress(), readBuffer, null);
        } catch (Exception e) {
            LOGGER.error("Tried to put incoming msg to readQueue but failed. ", e);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket reading end");
        }
    }
}
