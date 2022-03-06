/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.transport;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.utils.IpPortAddress;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates instance of a TransportConnector that uses java internal memory as a
 * connection transport. No Network traffic is being produced.
 */
public class InMemoryCoapTransport extends BlockingCoapTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCoapTransport.class.getName());
    private final static InetAddress LOCALHOST;

    static {
        try {
            LOCALHOST = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private CoapReceiver coapReceiver;
    private final static BindingManager BINDING_MANAGER = new BindingManager();
    private final IpPortAddress bindingAddress;
    private final Executor executor;
    private TransportContext transportContext = TransportContext.EMPTY;


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
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        InMemoryCoapTransport transport = BINDING_MANAGER.getQueueByAddress(adr);

        if (transport != null) {
            transport.receive(new DatagramMessage(bindingAddress, coapPacket.toByteArray()));
        }
    }

    public void receive(DatagramMessage msg) {
        executor.execute(() -> {
            try {
                coapReceiver.handle(CoapPacket.read(msg.source.toInetSocketAddress(), msg.packetData, msg.packetData.length), transportContext);
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
