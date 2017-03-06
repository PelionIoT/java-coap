/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.mbed.coap.utils.IpPortAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates instance of a TransportConnector that uses java internal memory as a
 * connection transport. No Network traffic is being produced.
 *
 * @author szymon
 */
public class InMemoryTransport extends AbstractTransportConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryTransport.class.getName());
    public final static String LOCALHOST = "localhost";
    private TransportContext transportContext;
    private BlockingQueue<DatagramMessage> queue;
    private final static BindingManager BINDING_MANAGER = new BindingManager();


    public static InetSocketAddress createAddress(int port) {
        return BINDING_MANAGER.createAddress(port);
    }

    public static TransportConnector create(int port) {
        return new InMemoryTransport(createAddress(port));
    }

    public static TransportConnector create() {
        return new InMemoryTransport(BINDING_MANAGER.createRandomPortAddress());
    }

    public static TransportConnector create(InetSocketAddress bindSocket) {
        return new InMemoryTransport(bindSocket);
    }

    public InMemoryTransport(int port) {
        this(createAddress(port));
    }

    public InMemoryTransport(int port, Executor executor) {
        super(createAddress(port), executor);
    }

    public InMemoryTransport() {
        this(BINDING_MANAGER.createRandomPortAddress());
    }

    private InMemoryTransport(InetSocketAddress bindSocket) {
        super(bindSocket, Runnable::run);
    }

    @Override
    protected void initialize() throws IOException {
        queue = new LinkedBlockingQueue<>();
        BINDING_MANAGER.bind(new IpPortAddress(getBindSocket()), queue);
    }

    public void start() throws IOException {
        initialize();
    }

    @Override
    public void stop() {
        BINDING_MANAGER.unbind(new IpPortAddress(getBindSocket()));
        super.stop();
    }

    @Override
    public boolean performReceive() {
        try {
            DatagramMessage msg = queue.poll(1, TimeUnit.SECONDS);
            if (msg != null) {
                transportReceived(msg.source.toInetSocketAddress(), msg.packetData, getTransportContext());
                return true;
            }
        } catch (InterruptedException ex) {
            //ignore
        }
        return false;
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress adr, TransportContext transContext) throws IOException {
        BlockingQueue<DatagramMessage> q = BINDING_MANAGER.getQueueByAddress(adr);
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

    static class BindingManager {
        private final Random random;
        private final Map<IpPortAddress, BlockingQueue<DatagramMessage>> BIND_CONNECTORS = new ConcurrentHashMap<>();

        BindingManager() {
            this(new Random());
        }

        BindingManager(Random rnd) {
            random = rnd;
        }

        public InetSocketAddress createAddress(int port) {
            if (port == 0) {
                return new InetSocketAddress(LOCALHOST, getFreePort());
            }
            return new InetSocketAddress(LOCALHOST, port);
        }

        public InetSocketAddress createRandomPortAddress() {
            return createAddress(0);
        }

        public int getFreePort() {
            while (true) {
                int port = random.nextInt(0xFFFE) + 1;
                if (!BIND_CONNECTORS.containsKey(new IpPortAddress(createAddress(port)))) {
                    LOGGER.trace("getFreePort() " + port);
                    return port;
                }
            }

        }

        public void bind(IpPortAddress addr, BlockingQueue<DatagramMessage> queue) {
            BIND_CONNECTORS.put(addr, queue);
        }

        public void unbind(IpPortAddress addr) {
            BIND_CONNECTORS.remove(addr);
        }

        public BlockingQueue<DatagramMessage> getQueueByAddress(InetSocketAddress adr) {
            return BIND_CONNECTORS.get(new IpPortAddress(adr));
        }
    }
}
