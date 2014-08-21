/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
public class DatagramChannelTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramChannelTransport.class);
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
                LOGGER.debug("DatagramChannel [receiveBuffer: {}, sendBuffer: {}]", channel.socket().getReceiveBufferSize(), channel.socket().getSendBufferSize());
            }

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
            channel.close();
            if (channel.isOpen()) {
                LOGGER.warn("DatagramChannel stays open");
                try {
                    Thread.sleep(300);
                    if (channel.isOpen()) {
                        LOGGER.warn("DatagramChannel is still open!");
                    }
                } catch (InterruptedException ex) {
                    //do nothing
                }
            }
            channel = null;
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        if (channel == null) {
            if (this.isRunning) {
                LOGGER.error("UDPConnectorChannel is NULL, reinitializing");
                initialize();
                if (channel == null) {
                    LOGGER.error("DatagramChannel is still NULL I give up!!!!!!");
                    return;
                }
            } else {
                LOGGER.error("UDPConnectorChannel is closed");
                return;
            }
        }

        try {
            int res = channel.send(ByteBuffer.wrap(data, 0, len), adr);
            if (res != len) {
                LOGGER.error("Did not send full datagram " + res + " =! " + len);
            }
        } catch (ClosedChannelException ex) {
            //Probably Java BUG
            LOGGER.error("Could not send datagram packet, trying again", ex);

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
                    transReceiver.onReceive(sourceAddress, buffer, TransportContext.NULL);
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
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    public void setReuseAddress(boolean channelReuseAddress) {
        this.channelReuseAddress = channelReuseAddress;
    }
}
