/*
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
import java.nio.channels.ServerSocketChannel;
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
public class TCPServerConnector extends TCPConnector implements TransportConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(TCPServerConnector.class);
    private ServerSocketChannel ssChannel;
    private final InetSocketAddress bindAddress;

    /**
     * Constructs TCPServerConnector and binds it to given address with maximum
     * message size of {@link org.mbed.coap.tcp.TCPConnector#DEFAULT_MAX_LENGTH}
     *
     * @param bindingSocketAddress address of binding socket
     */
    public TCPServerConnector(InetSocketAddress bindingSocketAddress) {
        this(bindingSocketAddress, DEFAULT_MAX_LENGTH);
    }
    
    /**
     * Constructs TCPServerConnector and binds it to given address with given
     * maximum message size
     *
     * @param bindingSocketAddress address of binding socket
     * @param maxMessageSize maximum message size the connector can receive or
     * send
     */
    public TCPServerConnector(InetSocketAddress bindingSocketAddress, int maxMessageSize) {
        this(bindingSocketAddress, maxMessageSize, 0);
    }
    
    /**
     * Constructs TCPServerConnector and binds it to given address with given
     * maximum message size and given idle timeout value (seconds)
     *
     * @param bindingSocketAddress address of binding socket
     * @param maxMessageSize maximum message size the connector can receive or
     * send
     * @param idleTimeout idle timeout in seconds how long to keep TCP socket open
     */
    public TCPServerConnector(InetSocketAddress bindingSocketAddress, int maxMessageSize, int idleTimeout) {
        super(maxMessageSize, idleTimeout);
        this.bindAddress = bindingSocketAddress;
    }

    @Override
    public void stop() {
        super.stop();
        try {
            ssChannel.close();
        } catch (Exception e) {
            LOGGER.warn("Stopping Server channel caused error ", e);
        }
    }

    @Override
    @SuppressWarnings("PMD.AvoidRethrowingException") //catch everything but IOException
    public void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        if (len > MAX_LENGTH) {
            LOGGER.warn("Too long message to send, length: " + len + " max-length: " + MAX_LENGTH);
            throw new IOException("Too long message to send.");
        }
        synchronized (changeRequests) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send queue filling to " + destinationAddress);
            }
            try {
                fillQueueWithData(data, len, destinationAddress);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("WriteQueue put caused exception ", e);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send queue filling, waking selector");
            }
            selector.wakeup();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send queue filling, done");
            }
        }
    }

    private void fillQueueWithData(byte[] data, int len, InetSocketAddress destinationAddress) throws IOException {
        SocketChannel socketChannel = sockets.get(destinationAddress);
        if (socketChannel == null || !socketChannel.isOpen()) {
            throw new IOException("No Connection to " + destinationAddress);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Send queue filling, adding changerequest to socketchannel " + socketChannel);
        }
        changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
        List<ByteBuffer> queue = pendingData.get(socketChannel);
        if (queue == null) {
            queue = new ArrayList<>();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Send queue filling, creating new queue");
            }
            pendingData.put(socketChannel, queue);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Send queue filling, adding to queue");
        }
        ByteBuffer allData = ByteBuffer.allocate(len + LENGTH_BYTES);
        allData.putInt(len);
        allData.put(data, 0, len);
        queue.add(ByteBuffer.wrap(allData.array()));
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) ssChannel.socket().getLocalSocketAddress();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                synchronized (changeRequests) {
                    for (ChangeRequest change : changeRequests) {
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                if (key != null) {
                                    try {
                                        key.interestOps(change.ops);
                                    } catch (CancelledKeyException ckE) {
                                        LOGGER.debug("CancelledKeyException: " + change.socket);
                                    }
                                } else {
                                    LOGGER.debug("Key was null for " + change.socket);
                                    change.socket.close();
                                }
                                break;
                            default:
                            //ignore
                        }
                    }
                    changeRequests.clear();
                }
                // Wait for an event one of the registered channels
                selector.select();

                // Iterate over the set of keys for which events are available
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                if (isRunning) {
                    LOGGER.error("Closed Selector Exception", e);
                    try {
                        Selector socketSelector = SelectorProvider.provider().openSelector();
                        ssChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
                        selector = socketSelector;
                    } catch (Exception ssE) {
                        LOGGER.error("Cannot reinitialize selector!", ssE);
                    }
                } else {
                    LOGGER.warn("ClosedSelectorException. Connector stopped");
                }
            } catch (Exception e) {
                if (isRunning) {
                    LOGGER.error("Cannot make selector select, trying again. ", e);
                } else {
                    LOGGER.warn("Cannot make selector select. Connector stopped. (" + e.getMessage() + ")");
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket accept");
        }
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.socket().getRemoteSocketAddress();
        sockets.put(remoteAddress, socketChannel);
        resetTimer(remoteAddress);
        oldReadBuffer.remove(remoteAddress);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket accepted was " + socketChannel);
            LOGGER.trace("Socket address " + socketChannel.socket().getRemoteSocketAddress());
            LOGGER.trace("Sockets length is " + sockets.size());
        }
        socketChannel.register(selector, SelectionKey.OP_READ);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket accept end");
        }
    }

    private void write(SelectionKey key) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket writing to " + key);
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            resetTimer((InetSocketAddress)socketChannel.getRemoteAddress());
        } catch (IOException e) {
            LOGGER.warn("Cannot reset timer for socket " + socketChannel);
        }
        try {
            synchronized (changeRequests) {
                List<ByteBuffer> queue = pendingData.get(socketChannel);

                // Write until there's not more data ...
                while (!queue.isEmpty()) {
                    ByteBuffer buf = queue.get(0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Writing, limit:" + buf.limit() + " position:" + buf.position() + " remaining:" + buf.remaining());
                    }
                    try {
                        socketChannel.write(buf);
                    } catch (Exception ex) {
                        // Cannot write to socket, probably closed and cannot recover.
                        // Do cleanup the connection.
                        LOGGER.error("Cannot write to socket, closing and cleaning up." + socketChannel.getRemoteAddress());
                        try {
                            socketChannel.close();
                            key.cancel();
                            LOGGER.debug("Closed and cleaned up.");
                        } catch (Exception cE) {
                            LOGGER.warn("Cannot clean up connection, ", cE);
                        }
                        queue.clear();
                        break;
                    }
                    if (buf.remaining() > 0) {
                        // ... or the socket's buffer fills up
                        break;
                    }
                    queue.remove(0);
                }

                if (queue.isEmpty()) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Mark READ op");
                    }
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot write to socket.", e);
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Socket writing end");
        }
    }

    @Override
    protected Selector initSelector() throws IOException {
        Selector socketSelector = SelectorProvider.provider().openSelector();
        ssChannel = ServerSocketChannel.open();
        ssChannel.configureBlocking(false);
        ssChannel.socket().bind(this.bindAddress);
        ssChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
        return socketSelector;
    }

    @Override
    protected String getThreadName() {
        return "tcp-server-connector";
    }
}
