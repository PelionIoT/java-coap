package org.mbed.coap.udp;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;

/**
 * DatagramSocket implementation that changes traffic class in non-synchronous
 * way to avoid blocking with receive method.
 *
 * @author szymon
 */
public class QoSDatagramSocket extends DatagramSocket {

    private final DatagramSocketImpl impl;

    public QoSDatagramSocket(SocketAddress bindSocketAddress) throws SocketException {
        super(bindSocketAddress);
        try {
            Field implField = DatagramSocket.class.getDeclaredField("impl");
            implField.setAccessible(true);

            this.impl = (DatagramSocketImpl) implField.get(this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        }
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        impl.setOption(SocketOptions.IP_TOS, tc);

    }

}
