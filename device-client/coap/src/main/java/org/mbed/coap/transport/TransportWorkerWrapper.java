/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker implementation for transport connector.
 *
 * @author szymon
 */
public class TransportWorkerWrapper implements Runnable, TransportConnector {
//TODO: rename to TransportWorker

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportWorkerWrapper.class);
    private final TransportConnectorTask wrappedConnector;
    protected boolean isRunning;
    private Thread[] transportThreads;
    private int transThreadsCount = 1;

    public TransportWorkerWrapper(TransportConnectorTask wrappedConnector) {
        this.wrappedConnector = wrappedConnector;
    }

    public TransportWorkerWrapper(TransportConnectorTask wrappedConnector, int transThreadsCount) {
        this.wrappedConnector = wrappedConnector;
        this.transThreadsCount = transThreadsCount;
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                wrappedConnector.performReceive();
            } catch (Throwable ex) {    //NOPMD  bug in executor when any Exception is thrown
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void start(TransportReceiver udpReceiver) throws IOException {
        wrappedConnector.start(udpReceiver);
        isRunning = true;
        transportThreads = new Thread[transThreadsCount];
        for (int i = 0; i < transThreadsCount; i++) {
            transportThreads[i] = new Thread(this, "transport-" + i);
            transportThreads[i].start();
        }

    }

    @Override
    public void stop() {
        isRunning = false;
        wrappedConnector.stop();

        for (Thread th : transportThreads) {
            if (th != null) {
                th.interrupt();
            }
        }
    }

    @Override
    public void send(byte[] data, int len, InetSocketAddress destinationAddress, TransportContext transContext) throws IOException {
        wrappedConnector.send(data, len, destinationAddress, transContext);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return wrappedConnector.getLocalSocketAddress();
    }
}
