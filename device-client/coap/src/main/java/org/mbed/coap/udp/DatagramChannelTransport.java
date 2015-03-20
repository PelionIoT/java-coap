/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
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
import org.mbed.coap.transport.TransportReceiver;

/**
 * @author szymon
 */
public class DatagramChannelTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = Logger.getLogger(DatagramChannelTransport.class.getName());
    protected DatagramChannel channel;
    protected boolean channelReuseAddress;
    protected boolean channelConfigureBlocking = true;
    private int socketBufferSize = -1;

    public DatagramChannelTransport(int localPort) {
        super(localPort);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket) {
        super(bindingSocket);
    }

    public DatagramChannelTransport(InetSocketAddress bindingSocket, boolean useBlockingMode) {
        super(bindingSocket);
        this.channelConfigureBlocking = useBlockingMode;
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
            if (channel.isOpen()) {
                LOGGER.warning("DatagramChannel stays open");
                try {
                    Thread.sleep(300);
                    if (channel.isOpen()) {
                        LOGGER.warning("DatagramChannel is still open!");
                    }
                } catch (InterruptedException ex) {
                    //do nothing
                }
            }
            channel = null;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        if (channel == null) {
            if (this.isRunning) {
                LOGGER.severe("UDPConnectorChannel is NULL, reinitializing");
                initialize();
                if (channel == null) {
                    LOGGER.severe("DatagramChannel is still NULL I give up!!!!!!");
                    return;
                }
            } else {
                LOGGER.severe("UDPConnectorChannel is closed");
                return;
            }
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
    protected boolean receive(TransportReceiver transReceiver) {
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
