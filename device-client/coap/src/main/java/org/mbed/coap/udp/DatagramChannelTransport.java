/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.transport.TransportContext;

/**
 * @author szymon
 */
public class DatagramChannelTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = Logger.getLogger(DatagramChannelTransport.class.getName());
    protected DatagramChannel channel;
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
        super(localPort);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket) {
        super(bindingSocket);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket, boolean useBlockingMode) {
        this(bindingSocket, useBlockingMode, true);
    }

    /**
     * Constructs DatagramChannelTransport.
     *
     * @param bindingSocket binding socket address
     * @param useBlockingMode if true then uses blocking mode
     * @param initThreadReader if true then initialize internal thread that reads incoming packets
     */
    public DatagramChannelTransport(InetSocketAddress bindingSocket, boolean useBlockingMode, boolean initThreadReader) {
        super(bindingSocket, initThreadReader);
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
        if (channel != null) {
            throw new IllegalStateException();
        }
        this.socketBufferSize = socketBufferSizeBytes;
    }

    @Override
    protected void initialize() throws IOException {
        try {
            channel = createChannel();
            channel.configureBlocking(channelConfigureBlocking);

            if (socketBufferSize > 0) {
                channel.socket().setReceiveBufferSize(socketBufferSize);
                channel.socket().setSendBufferSize(socketBufferSize);
            }
            channel.socket().setReuseAddress(channelReuseAddress);
            channel.socket().bind(getBindSocket());

            LOGGER.info("CoAP server binds on " + channel.socket().getLocalSocketAddress());
            if (socketBufferSize > 0) {
                LOGGER.fine("DatagramChannel [receiveBuffer: " + channel.socket().getReceiveBufferSize() + ", sendBuffer: " + channel.socket().getSendBufferSize() + "]");
            }

        } catch (BindException ex) {
            LOGGER.severe("Can not bind on " + getBindSocket());
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
            channel.close();
            channel = null;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        if (channel == null) {
            throw new IllegalStateException("Sending when DatagramChannel is not started.");
        }

        try {
            int res = channel.send(ByteBuffer.wrap(data, 0, len), adr);
            if (res != len) {
                LOGGER.severe("Did not send full datagram " + res + " =! " + len);
            }
        } catch (ClosedChannelException ex) {
            //Probably Java BUG
            LOGGER.log(Level.SEVERE, "Could not send datagram packet, trying again", ex);

            //this.channel = null;
            initialize();
            //try again to send
            channel.send(ByteBuffer.wrap(data, 0, len), adr);
        }
    }

    @Override
    public boolean performReceive() {
        ByteBuffer buffer = getBuffer();
        try {
            if (channel != null) {
                InetSocketAddress sourceAddress = (InetSocketAddress) channel.receive(buffer);
                if (sourceAddress != null) {
                    transReceiver.onReceive(sourceAddress, createCopyOfPacketData(buffer, buffer.position()), TransportContext.NULL);
                    return true;
                } else {
                    return false;
                }
            }

        } catch (ClosedChannelException ex) {
            if (isRunning()) {
                try {
                    LOGGER.severe("DatagramChannel closed, reopening");
                    initialize();
                } catch (IOException ex1) {
                    LOGGER.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    public void setReuseAddress(boolean channelReuseAddress) {
        this.channelReuseAddress = channelReuseAddress;
    }
}
