/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author KALLE
 */
public class TCPClientConnector extends TCPConnector implements TransportConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(TCPClientConnector.class);

    /**
     * Constructs TCP client connector with default message size
     * {@link org.mbed.coap.tcp.TCPConnector#DEFAULT_MAX_LENGTH}
     */
    public TCPClientConnector() {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client constructor");
        }
    }

    /**
     * Constructs TCP client connector with defined message size
     *
     * @param maxMessageSize maximum message size to be able receive or send
     */
    public TCPClientConnector(int maxMessageSize) {
        super(maxMessageSize);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client constructor");
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        if (len > MAX_LENGTH) {
            LOGGER.warn("Too long message to send, length: " + len + " max-length: " + MAX_LENGTH);
            throw new IOException("Too long message to send.");
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client Send queue filling");
        }
        synchronized (changeRequests) {
            try {
                SocketChannel socketChannel = sockets.get(destinationAddress);
                if (socketChannel == null) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client Creating new connection to server");
                    }
                    socketChannel = initiateConnection(destinationAddress);
                } else {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client Send queue filling, adding changerequest");
                    }
                    changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
                }
                List<ByteBuffer> queue = pendingData.get(socketChannel);
                if (queue == null) {
                    queue = new ArrayList<>();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client Send queue filling, creating new queue");
                    }
                    pendingData.put(socketChannel, queue);
                }
                ByteBuffer allData = ByteBuffer.allocate(len + LENGTH_BYTES);
                allData.putInt(len);
                allData.put(data, 0, len);
                queue.add(ByteBuffer.wrap(allData.array()));
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Client Send queue filling, adding to queue [len:{}]", allData.capacity());
                }
            } catch (Exception e) {
                LOGGER.error("Client WriteQueue put caused exception ", e);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Client Send queue filling, waking selector");
            }
            selector.wakeup();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Client Send queue filling, done");
            }
        }
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return null;
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Client run procedure");
                }
                synchronized (changeRequests) {
                    for (ChangeRequest change : changeRequests) {
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(selector);
                                if (key == null) {
                                    LOGGER.debug("key for channel is null " + change.socket);
                                    continue;
                                }
                                try {
                                    key.interestOps(change.ops);
                                } catch (CancelledKeyException ckE) {
                                    LOGGER.debug("CancelledKeyException " + change.socket);
                                    continue;
                                }
                                break;
                            case ChangeRequest.REGISTER:
                                change.socket.register(selector, change.ops);
                                break;
                            default:
                                LOGGER.warn("Un-handled type: " + change.type);
                        }
                    }
                    changeRequests.clear();
                }
                // Wait for an event one of the registered channels
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Client selecting");
                }
                int num = selector.select();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Client selected [" + num + "]");
                }

                // Iterate over the set of keys for which events are available
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client found selection key " + key);
                    }
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        finishConnection(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                LOGGER.debug("Client Selector closed");
            } catch (Exception e) {
                LOGGER.error("Client Cannot make selector select, trying again. ", e);
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client Socket writing");
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (changeRequests) {
            List<ByteBuffer> queue = pendingData.get(socketChannel);
            try {

                // Write until there's not more data ...
                while (!queue.isEmpty()) {
                    ByteBuffer buf = queue.get(0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client writing, limit:" + buf.limit() + " position:" + buf.position() + " remaining:" + buf.remaining());
                    }
                    socketChannel.write(buf);
                    if (buf.remaining() > 0) {
                        // ... or the socket's buffer fills up
                        break;
                    }
                    queue.remove(0);
                }

                if (queue.isEmpty()) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Client mark READ op");
                    }
                    key.interestOps(SelectionKey.OP_READ);
                }
            } catch (Exception e) {
                LOGGER.error("Client Cannot write to socket.", e);
                queue.clear();
                sockets.remove(socketChannel.socket().getRemoteSocketAddress());
                key.cancel();
                socketChannel.close();
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client Socket writing end");
        }
    }

    @Override
    protected Selector initSelector() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

    @Override
    protected String getThreadName() {
        return "tcp-client-connector";
    }

    private void finishConnection(SelectionKey key) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client finishing connection");
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            socketChannel.finishConnect();
            InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.socket().getRemoteSocketAddress();
            sockets.put(remoteAddress, socketChannel);
            oldReadBuffer.remove(remoteAddress);
        } catch (IOException e) {
            LOGGER.error("Client cannot finish connection " + e);
            key.cancel();
            return;
        }
        key.interestOps(SelectionKey.OP_WRITE);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client finished connection");
        }
    }

    private SocketChannel initiateConnection(InetSocketAddress address) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client initiating connection to " + address);
        }
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(address);

        synchronized (changeRequests) {
            changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Client initiated connection to " + address);
        }
        return socketChannel;
    }

    /**
     * Add Socket to internal socket list. It will be used when making request
     * to given destination address.
     *
     * @param destinationAddress destination address
     * @param socketChannel Socket channel
     */
    public void addSocketChannel(InetSocketAddress destinationAddress, SocketChannel socketChannel) {

        if (!socketChannel.isConnected()) {
            throw new IllegalStateException("Socket is not connected! " + socketChannel);
        }

        synchronized (changeRequests) {
            changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_READ));
            sockets.put(destinationAddress, socketChannel);
            oldReadBuffer.remove(destinationAddress);
            if (pendingData.get(socketChannel) == null) {
                pendingData.put(socketChannel, new ArrayList<ByteBuffer>());
            }
            selector.wakeup();
        }
    }
}
