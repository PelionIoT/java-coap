/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationNotEstablishedException;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.utils.Callback;

/**
 * Implements CoAP observe mechanism for CoAP Server.
 * (draft-ietf-core-observe-11)
 *
 * @author szymon
 */
public class CoapServerObserve extends CoapServerBlocks {

    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();

    protected CoapServerObserve(TransportConnector trans, Executor executor) {
        super(trans, executor, new CoapIdContextImpl());
    }

    protected CoapServerObserve(TransportConnector udp, Executor executor, CoapIdContext idContext) {
        super(udp, executor, idContext);
    }

    public CoapServerObserve() {
        super();
    }

    /**
     * Initialize observation.
     *
     * <p/>
     * <i>Asynchronous method</i>
     *
     * @param uri resource path for observation
     * @param destination destination address
     * @param respCallback handles observation response
     * @return observation identification (token)
     * @throws CoapException
     */
    public byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback) throws CoapException {
        return observe(uri, destination, respCallback, observationIDGenerator.nextObservationID(uri));
    }

    /**
     * Initialize observation.
     *
     * <p/>
     * <i>Asynchronous method</i>
     *
     * @param uri resource path for observation
     * @param destination destination address
     * @param respCallback handles observation response
     * @param token observation identification (token)
     * @return observation identification
     * @throws CoapException
     */
    public byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token) throws CoapException {
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, uri, destination);
        request.setToken(token);
        request.headers().setObserve(0);
        return observe(request, respCallback);
    }

    public byte[] observe(CoapPacket request, final Callback<CoapPacket> respCallback) throws CoapException {
        if (request.headers().getObserve() == null) {
            request.headers().setObserve(0);
        }
        if (request.getToken() == null || request.getToken() == CoapPacket.DEFAULT_TOKEN) {
            request.setToken(observationIDGenerator.nextObservationID(request.headers().getUriPath()));
        }
        makeRequest(request, new Callback<CoapPacket>() {

            @Override
            public void callException(Exception ex) {
                respCallback.callException(ex);
            }

            @Override
            public void call(CoapPacket resp) {
                if (resp.getCode() == Code.C205_CONTENT && resp.headers().getObserve() == null) {
                    respCallback.callException(new ObservationNotEstablishedException(resp));
                    return;
                }
                respCallback.call(resp);
            }
        });
        return request.getToken();
    }

    /**
     * Sets observation id generator instance.
     *
     * @param observationIDGenerator observation id generator instance
     */
    public void setObservationIDGenerator(ObservationIDGenerator observationIDGenerator) {
        this.observationIDGenerator = observationIDGenerator;

    }

    /**
     * Interface for generating observation IDs.
     */
    public static interface ObservationIDGenerator {    //NOPMD

        /**
         * Returns next observation id.
         *
         * @param uri URI path
         * @return observation id
         */
        byte[] nextObservationID(String uri);
    }

    /**
     * Provides simple implementation of {@link ObservationIDGenerator}
     */
    public static class SimpleObservationIDGenerator implements ObservationIDGenerator {

        private long token;

        public SimpleObservationIDGenerator() {
            token = 0xFFFF & (new Random()).nextLong();
        }

        public SimpleObservationIDGenerator(int initToken) {
            token = initToken;
        }

        @Override
        public synchronized byte[] nextObservationID(String uri) {
            return HeaderOptions.convertVariableUInt(++token);
        }
    }
}
