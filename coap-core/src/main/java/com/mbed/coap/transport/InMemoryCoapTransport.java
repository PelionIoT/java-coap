/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.IpPortAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates instance of a TransportConnector that uses java internal memory as a
 * connection transport. No Network traffic is being produced.
 *
 * @author szymon
 */
public class InMemoryCoapTransport implements CoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCoapTransport.class.getName());
    public final static String LOCALHOST = "localhost";

    private CoapReceiver coapReceiver;
    private final static BindingManager BINDING_MANAGER = new BindingManager();
    private final IpPortAddress bindingAddress;
    private final Executor executor;
    private TransportContext transportContext = TransportContext.NULL;


    public static InetSocketAddress createAddress(int port) {
        return BINDING_MANAGER.createAddress(port);
    }

    public static CoapTransport create(int port) {
        return new InMemoryCoapTransport(createAddress(port), Runnable::run);
    }

    public static CoapTransport create() {
        return new InMemoryCoapTransport(BINDING_MANAGER.createRandomPortAddress(), Runnable::run);
    }

    public static CoapTransport create(InetSocketAddress bindSocket) {
        return new InMemoryCoapTransport(bindSocket, Runnable::run);
    }

    public InMemoryCoapTransport(int port) {
        this(createAddress(port), Runnable::run);
    }

    public InMemoryCoapTransport(int port, Executor executor) {
        this(createAddress(port), executor);
    }

    public InMemoryCoapTransport() {
        this(BINDING_MANAGER.createRandomPortAddress(), Runnable::run);
    }

    private InMemoryCoapTransport(InetSocketAddress bindSocket, Executor executor) {
        this.bindingAddress = IpPortAddress.of(bindSocket);
        this.executor = executor;
    }


    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        BINDING_MANAGER.bind(bindingAddress, this);
        this.coapReceiver = coapReceiver;
    }

    @Override
    public void stop() {
        BINDING_MANAGER.unbind(bindingAddress);
    }

    @Override
    public void sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        InMemoryCoapTransport transport = BINDING_MANAGER.getQueueByAddress(adr);

        if (transport != null) {
            transport.receive(new DatagramMessage(bindingAddress, coapPacket.toByteArray()));
        }
    }

    public void receive(DatagramMessage msg) {
        executor.execute(() -> {
            try {
                coapReceiver.handle(CoapPacket.read(msg.packetData, msg.packetData.length, msg.source.toInetSocketAddress()), transportContext);
            } catch (CoapException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return bindingAddress.toInetSocketAddress();
    }

    public void setTransportContext(TransportContext transportContext) {
        this.transportContext = transportContext;
    }

    public static class DatagramMessage {

        private final IpPortAddress source;
        private final byte[] packetData;

        public DatagramMessage(IpPortAddress source, byte[] packetData) {
            this.source = source;
            this.packetData = packetData;
        }

    }

    static class BindingManager {
        private final Random random;
        private final Map<IpPortAddress, InMemoryCoapTransport> TRANSPORTS = new ConcurrentHashMap<>();

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
                if (!TRANSPORTS.containsKey(new IpPortAddress(createAddress(port)))) {
                    LOGGER.trace("getFreePort() " + port);
                    return port;
                }
            }

        }

        public void bind(IpPortAddress addr, InMemoryCoapTransport queue) {
            TRANSPORTS.put(addr, queue);
        }

        public void unbind(IpPortAddress addr) {
            TRANSPORTS.remove(addr);
        }

        public InMemoryCoapTransport getQueueByAddress(InetSocketAddress adr) {
            return TRANSPORTS.get(new IpPortAddress(adr));
        }
    }
}
