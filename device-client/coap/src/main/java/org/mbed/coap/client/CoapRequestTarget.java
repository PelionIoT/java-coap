/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import java.util.concurrent.Future;
import org.mbed.coap.BlockOption;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.ObservationNotEstablishedException;
import org.mbed.coap.server.CoapServerObserve;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.FutureCallbackAdapter;

/**
 * CoAP request builder.
 *
 * @author szymon
 */
public class CoapRequestTarget {

    private final CoapPacket requestPacket;
    private BlockSize blockSize;
    private final CoapClient coapClient;
    private TransportContext transContext = TransportContext.NULL;

    CoapRequestTarget(String path, final CoapClient coapClient) {
        this.coapClient = coapClient;
        this.requestPacket = new CoapPacket(Method.GET, MessageType.Confirmable, path, coapClient.getDestination());
    }

    /**
     * Provide byte array payload
     *
     * @param payload payload
     * @param contentFormat content-format
     */
    public CoapRequestTarget payload(byte[] payload, short contentFormat) {
        return payload(payload, (int) contentFormat);
    }

    public CoapRequestTarget payload(byte[] payload, int contentFormat) {
        requestPacket.setPayload(payload);
        requestPacket.headers().setContentFormat((short) contentFormat);
        return this;
    }

    public CoapRequestTarget payload(String payload, int contentFormat) {
        requestPacket.setPayload(payload);
        requestPacket.headers().setContentFormat((short) contentFormat);
        return this;
    }

    public CoapRequestTarget payload(String payload) {
        requestPacket.setPayload(payload);
        return this;
    }

    public CoapRequestTarget payload(byte[] payload) {
        requestPacket.setPayload(payload);
        return this;
    }

    public CoapRequestTarget token(byte[] token) {
        requestPacket.setToken(token);
        return this;
    }

    public CoapRequestTarget token(long token) {
        requestPacket.setToken(HeaderOptions.convertVariableUInt(token));
        return this;
    }

    public CoapRequestTarget query(String name, String val) {
        if (name.isEmpty() || name.contains("=") || name.contains("&") || name.contains("?")
                || val.isEmpty() || val.contains("=") || val.contains("&") || val.contains("?")) {
            throw new IllegalArgumentException("Non valid characters provided in query");
        }
        final StringBuilder query = new StringBuilder();
        if (requestPacket.headers().getUriQuery() != null) {
            query.append(requestPacket.headers().getUriQuery());
            query.append('&');
        }
        query.append(name).append('=').append(val);
        requestPacket.headers().setUriQuery(query.toString());
        return this;
    }

    public CoapRequestTarget query(String uriQuery) {
        requestPacket.headers().setUriQuery(uriQuery);
        return this;
    }

    public CoapRequestTarget accept(short contentFormat) {
        requestPacket.headers().setAccept(new short[]{contentFormat});
        return this;
    }

    public CoapRequestTarget etag(byte[] etag) {
        requestPacket.headers().setEtag(etag);
        return this;
    }

    public CoapRequestTarget ifMatch(byte[] etag) {
        requestPacket.headers().setIfMatch(new byte[][]{etag});
        return this;
    }

    public CoapRequestTarget ifNotMatch() {
        return ifNotMatch(true);
    }

    public CoapRequestTarget blockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
        return this;
    }

    public CoapRequestTarget ifNotMatch(boolean ifNotMatchVal) {
        requestPacket.headers().setIfNonMatch(ifNotMatchVal);
        return this;
    }

    public CoapRequestTarget host(String testhost) {
        requestPacket.headers().setUriHost(testhost);
        return this;
    }

    public CoapRequestTarget maxAge(long maxAge) {
        requestPacket.headers().setMaxAge(maxAge);
        return this;
    }

    public CoapRequestTarget header(int num, byte[] data) {
        requestPacket.headers().put(num, data);
        return this;
    }

    /**
     * Sets transport context that will be passed to transport connector.
     *
     * @param context transport context
     */
    public CoapRequestTarget context(TransportContext context) {
        this.transContext = context;
        return this;
    }

    /**
     * Marks request as non-confirmable.
     */
    public CoapRequestTarget non() {
        requestPacket.setMessageType(MessageType.NonConfirmable);
        return this;
    }

    /**
     * Marks request as confirmable (default).
     */
    public CoapRequestTarget con() {
        requestPacket.setMessageType(MessageType.Confirmable);
        return this;
    }

    public Future<CoapPacket> get() throws CoapException {
        updatePacketWithBlock2();
        return request();
    }

    public void get(Callback<CoapPacket> callback) throws CoapException {
        updatePacketWithBlock2();
        request(callback);
    }

    public Future<CoapPacket> post() throws CoapException {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.POST);
        return request();
    }

    public void post(Callback<CoapPacket> callback) throws CoapException {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.POST);
        request(callback);
    }

    public Future<CoapPacket> delete() throws CoapException {
        requestPacket.setMethod(Method.DELETE);
        return request();
    }

    public void delete(Callback<CoapPacket> callback) throws CoapException {
        requestPacket.setMethod(Method.DELETE);
        request(callback);
    }

    public Future<CoapPacket> put() throws CoapException {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.PUT);
        return request();
    }

    public void put(Callback<CoapPacket> callback) throws CoapException {
        updatePacketWithBlock1();
        requestPacket.setMethod(Method.PUT);
        request(callback);
    }

    public Future<CoapPacket> observe(ObservationListener observationListener) throws CoapException {
        if (coapClient.coapServer instanceof CoapServerObserve) {
            CoapServerObserve obsServer = (CoapServerObserve) coapClient.coapServer;

            requestPacket.headers().setObserve(0);
            FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
            coapClient.putObservationListener(observationListener, obsServer.observe(requestPacket, callback), requestPacket.headers().getUriPath());

            return callback;
        } else {
            throw new ObservationNotEstablishedException();
        }
    }

    CoapPacket getRequestPacket() {
        return requestPacket;
    }

    private Future<CoapPacket> request() throws CoapException {
        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        request(callback);
        return callback;
    }

    private void request(Callback<CoapPacket> callback) throws CoapException {
        coapClient.coapServer.makeRequest(requestPacket, callback, transContext);
    }

    public SyncRequestTarget sync() {
        return new SyncRequestTarget(this);
    }

    private void updatePacketWithBlock1() {
        if (blockSize != null) {
            requestPacket.headers().setBlock1Req(new BlockOption(0, blockSize, true));
        }
    }

    private void updatePacketWithBlock2() {
        if (blockSize != null) {
            requestPacket.headers().setBlock2Res(new BlockOption(0, blockSize, false));
        }
    }

}
