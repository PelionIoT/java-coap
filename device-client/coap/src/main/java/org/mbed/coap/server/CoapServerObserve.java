/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.Random;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationNotEstablishedException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.DataConvertingUtility;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.utils.Callback;

/**
 * Implements CoAP observe mechanism for CoAP Server. (draft-ietf-core-observe-11)
 *
 * @author szymon
 */
public class CoapServerObserve extends CoapServerBlocks {

    private ObservationIDGenerator observationIDGenerator = new SimpleObservationIDGenerator();

    protected CoapServerObserve() {
        super();
    }

    @Override
    public byte[] observe(String uri, InetSocketAddress destination, final Callback<CoapPacket> respCallback, byte[] token) throws CoapException {
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, uri, destination);
        request.setToken(token);
        request.headers().setObserve(0);
        return observe(request, respCallback);
    }

    @Override
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
    public interface ObservationIDGenerator {

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
            return DataConvertingUtility.convertVariableUInt(++token);
        }
    }
}
