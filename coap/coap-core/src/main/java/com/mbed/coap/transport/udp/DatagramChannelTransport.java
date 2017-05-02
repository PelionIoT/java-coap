/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport.udp;

import com.mbed.coap.transport.AbstractTransportConnector;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class DatagramChannelTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramChannelTransport.class.getName());
    protected AtomicReference<DatagramChannel> channel = new AtomicReference<>();
    protected boolean channelReuseAddress;
    protected boolean channelConfigureBlocking = true;
    private int socketBufferSize = -1;

    /**
     * Creates instance of DatagramChannelTransport that will bind to any local address on random port.
     *
     * @return instance of DatagramChannelTransport
     */
    public static DatagramChannelTransport ofRandomPort() {
        return new DatagramChannelTransport(0);
    }

    public DatagramChannelTransport(int localPort) {
        super(localPort, Runnable::run);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket, Executor receivedMessageWorker) {
        super(bindingSocket, receivedMessageWorker);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket, boolean useBlockingMode, Executor receivedMessageWorker) {
        this(bindingSocket, useBlockingMode, true, receivedMessageWorker);
    }

    /**
     * Constructs DatagramChannelTransport.
     *
     * @param bindingSocket binding socket address
     * @param useBlockingMode if true then uses blocking mode
     * @param initThreadReader if true then initialize internal thread that reads incoming packets
     */
    public DatagramChannelTransport(InetSocketAddress bindingSocket, boolean useBlockingMode, boolean initThreadReader, Executor receivedMessageWorker) {
        super(bindingSocket, initThreadReader, receivedMessageWorker);
        this.channelConfigureBlocking = useBlockingMode;
        if (!useBlockingMode && initThreadReader) {
            throw new IllegalStateException("Using internal thread reader without blocking mode would be highly CPU consuming.");
        }
    }

    /**
     * Sets socket receive and send buffer size.
     *
     * @param socketBufferSizeBytes socket buffer size in bytes
     */
    public void setSocketBufferSize(int socketBufferSizeBytes) {
        if (channel.get() != null) {
            throw new IllegalStateException();
        }
        this.socketBufferSize = socketBufferSizeBytes;
    }

    @Override
    protected void initialize() throws IOException {
        try {
            DatagramChannel newChannel = createChannel();
            newChannel.configureBlocking(channelConfigureBlocking);

            if (socketBufferSize > 0) {
                newChannel.socket().setReceiveBufferSize(socketBufferSize);
                newChannel.socket().setSendBufferSize(socketBufferSize);
            }
            newChannel.socket().setReuseAddress(channelReuseAddress);
            newChannel.socket().bind(getBindSocket());

            LOGGER.info("CoAP server binds on " + newChannel.socket().getLocalSocketAddress());
            if (socketBufferSize > 0) {
                LOGGER.debug("DatagramChannel [receiveBuffer: " + newChannel.socket().getReceiveBufferSize() + ", sendBuffer: " + newChannel.socket().getSendBufferSize() + "]");
            }
            channel.set(newChannel);

        } catch (BindException ex) {
            LOGGER.error("Can not bind on " + getBindSocket());
            throw ex;
        }
    }

    protected DatagramChannel createChannel() throws IOException {
        return DatagramChannel.open();
    }

    @Override
    public void stop() {
        super.stop();
        try {
            channel.getAndSet(null).close();
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        if (channel == null) {
            throw new IllegalStateException("Sending when DatagramChannel is not started.");
        }

        try {
            int res = channel.get().send(ByteBuffer.wrap(data, 0, len), adr);
            if (res != len) {
                LOGGER.error("Did not send full datagram " + res + " =! " + len);
            }
        } catch (ClosedChannelException ex) {
            //Probably Java BUG
            LOGGER.error("Could not send datagram packet, trying again", ex);

            //this.channel = null;
            initialize();
            //try again to send
            channel.get().send(ByteBuffer.wrap(data, 0, len), adr);
        }
    }

    @Override
    public boolean performReceive() {
        ByteBuffer buffer = getBuffer();
        try {
            if (channel != null) {
                InetSocketAddress sourceAddress = (InetSocketAddress) channel.get().receive(buffer);
                if (sourceAddress != null) {
                    transportReceived(sourceAddress, createCopyOfPacketData(buffer, buffer.position()), TransportContext.NULL);
                    return true;
                } else {
                    return false;
                }
            }

        } catch (ClosedChannelException ex) {
            if (isRunning()) {
                try {
                    LOGGER.error("DatagramChannel closed, reopening");
                    initialize();
                } catch (IOException ex1) {
                    LOGGER.error(ex1.getMessage(), ex1);
                }
            }
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) channel.get().socket().getLocalSocketAddress();
    }

    public void setReuseAddress(boolean channelReuseAddress) {
        this.channelReuseAddress = channelReuseAddress;
    }
}
