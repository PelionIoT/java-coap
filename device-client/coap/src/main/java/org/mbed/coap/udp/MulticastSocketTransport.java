/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.transport.TransportContext;

/**
 *
 * @author szymon
 */
public final class MulticastSocketTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = Logger.getLogger(MulticastSocketTransport.class.getName());
    private MulticastSocket mcastSocket;
    private final InetAddress mcastGroup;
    public final static String MCAST_LINKLOCAL_ALLNODES = "FF02::1";    //NOPMD
    public final static String MCAST_NODELOCAL_ALLNODES = "FF01::1";    //NOPMD

    public MulticastSocketTransport(InetSocketAddress bindSocket, String multicastGroup) throws UnknownHostException {
        super(bindSocket);
        mcastGroup = InetAddress.getByName(multicastGroup);
    }

    public MulticastSocketTransport(int port, String multicastGroup) throws UnknownHostException {
        super(port);
        mcastGroup = InetAddress.getByName(multicastGroup);
    }

    @Override
    public void stop() {
        super.stop();
        mcastSocket.close();
    }

    @Override
    protected void initialize() throws IOException {
        mcastSocket = new MulticastSocket(getBindSocket());
        mcastSocket.joinGroup(mcastGroup);
        LOGGER.fine("CoAP server binds on multicast " + mcastSocket.getLocalSocketAddress());
    }

    @Override
    public boolean performReceive() {
        try {
            ByteBuffer buffer = getBuffer();
            DatagramPacket datagramPacket = new DatagramPacket(buffer.array(), buffer.limit());
            mcastSocket.receive(datagramPacket);
            InetSocketAddress adr = (InetSocketAddress) datagramPacket.getSocketAddress();
            if (LOGGER.isLoggable(Level.FINE) && adr.getAddress().isMulticastAddress()) {
                LOGGER.fine("Received multicast message from: " + datagramPacket.getSocketAddress());
            }
            byte[] packetData = createCopyOfPacketData(buffer, datagramPacket.getLength());

            transReceiver.onReceive(adr, packetData, TransportContext.NULL);
            return true;
        } catch (SocketException ex) {
            if (isRunning()) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }
        } catch (IOException ex) {

            LOGGER.log(Level.WARNING, ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(data, len, adr);
        mcastSocket.send(datagramPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) mcastSocket.getLocalSocketAddress();
    }

}
