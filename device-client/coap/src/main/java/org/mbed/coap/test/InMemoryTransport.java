/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.transport.TransportWorkerWrapper;
import org.mbed.coap.udp.AbstractTransportConnector;
import org.mbed.coap.utils.IpPortAddress;

/**
 * Creates instance of a TransportConnector that uses java internal memory as a
 * connection transport. No Network traffic is being produced.
 *
 * @author szymon
 */
public class InMemoryTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = Logger.getLogger(InMemoryTransport.class.getName());
    public final static String LOCALHOST = "localhost";
    private static final Map<IpPortAddress, BlockingQueue<DatagramMessage>> BIND_CONNECTORS = new HashMap<>();
    private TransportContext transportContext;
    private BlockingQueue<DatagramMessage> queue;

    public static InetSocketAddress createAddress(int port) {
        if (port == 0) {
            return new InetSocketAddress(LOCALHOST, getFreePort());
        }
        return new InetSocketAddress(LOCALHOST, port);
    }

    private static void bind(IpPortAddress addr, BlockingQueue<DatagramMessage> queue) {
        BIND_CONNECTORS.put(addr, queue);
    }

    private static int getFreePort() {
        Random rnd = new Random();
        int port;
        while (true) {
            port = rnd.nextInt(0xFFFF);
            if (!BIND_CONNECTORS.containsKey(new IpPortAddress(createAddress(port)))) {
                LOGGER.finest("getFreePort() " + port);
                return port;
            }
        }

    }

    private static void unbind(IpPortAddress addr) {
        BIND_CONNECTORS.remove(addr);
    }

    public static TransportConnector create(int port) {
        return new TransportWorkerWrapper(new InMemoryTransport(createAddress(port)));
    }

    public static TransportConnector create() {
        return new TransportWorkerWrapper(new InMemoryTransport(createAddress(getFreePort())));
    }

    public static TransportConnector create(InetSocketAddress bindSocket) {
        return new TransportWorkerWrapper(new InMemoryTransport(bindSocket));
    }

    public InMemoryTransport(int port) {
        this(createAddress(port));
    }

    public InMemoryTransport() {
        this(createAddress(getFreePort()));
    }

    private InMemoryTransport(InetSocketAddress bindSocket) {
        super(bindSocket);
    }

    @Override
    protected void initialize() throws IOException {
        queue = new LinkedBlockingQueue<>();
        bind(new IpPortAddress(getBindSocket()), queue);
    }

    public void start() throws IOException {
        initialize();
    }

    @Override
    public void stop() {
        unbind(new IpPortAddress(getBindSocket()));
        super.stop();
    }

    @Override
    public boolean receive(TransportReceiver transReceiver) {
        try {
            DatagramMessage msg = queue.poll(1, TimeUnit.SECONDS);
            if (msg != null) {
                transReceiver.onReceive(msg.source.toInetSocketAddress(), msg.packetData, getTransportContext());
                return true;
            }
        } catch (InterruptedException ex) {
            //ignore
        }
        return false;
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        BlockingQueue<DatagramMessage> q = BIND_CONNECTORS.get(new IpPortAddress(adr));
        if (q != null) {
            q.add(new DatagramMessage(new IpPortAddress(getBindSocket()), data, len));
        }
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return getBindSocket();
    }

    protected TransportContext getTransportContext() {
        return transportContext;
    }

    public void setTransportContext(TransportContext transportContext) {
        this.transportContext = transportContext;
    }

    private static class DatagramMessage {

        private final IpPortAddress source;
        private final byte[] packetData;

        public DatagramMessage(IpPortAddress source, byte[] packetData, int packetLen) {
            this.source = source;
            if (packetData.length == packetLen) {
                this.packetData = packetData;
            } else {
                this.packetData = new byte[packetLen];
                System.arraycopy(packetData, 0, this.packetData, 0, packetLen);
            }
        }

    }
}
