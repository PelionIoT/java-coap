/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.FutureCallbackAdapter;

/**
 * CoAP client implementation.
 *
 * @author szymon
 */
public class CoapClient implements Closeable {

    private final InetSocketAddress destination;
    final CoapServer coapServer;
    private ObservationHandlerImpl observationHandler;

    public CoapClient(InetSocketAddress destination, CoapServer coapServer) {
        this.destination = destination;
        this.coapServer = coapServer;
        if (!coapServer.isRunning()) {
            throw new IllegalStateException("Coap server not running");
        }
    }

    /**
     * Creates request builder for provided uri-path.
     *
     * @param path uri path
     * @return request builder
     */
    public CoapRequestTarget resource(String path) {
        if (!coapServer.isRunning()) {
            throw new IllegalStateException("CoAP server not running");
        }
        if (path.contains("?") || path.contains("&")) {
            throw new IllegalArgumentException("Not supported character in path");
        }
        return new CoapRequestTarget(path, this);
    }

    public CompletableFuture<CoapPacket> ping() throws CoapException {
        FutureCallbackAdapter<CoapPacket> response = new FutureCallbackAdapter<>();
        CoapPacket pingRequest = new CoapPacket(null, MessageType.Confirmable, destination);
        coapServer.makeRequest(pingRequest, response);
        return response;
    }

    public void ping(Callback<CoapPacket> callback) throws CoapException {
        CoapPacket pingRequest = new CoapPacket(null, MessageType.Confirmable, destination);
        coapServer.makeRequest(pingRequest, callback);
    }

    /**
     * Close CoAP client connection.
     */
    @Override
    public void close() {
        coapServer.stop();
    }

    InetSocketAddress getDestination() {
        return destination;
    }

    void putObservationListener(ObservationListener observationListener, byte[] token, String uriPath) {
        if (observationHandler == null) {
            observationHandler = new ObservationHandlerImpl();
            coapServer.setObservationHandler(observationHandler);
        }
        observationHandler.putObservationListener(observationListener, token, uriPath);
    }

}
