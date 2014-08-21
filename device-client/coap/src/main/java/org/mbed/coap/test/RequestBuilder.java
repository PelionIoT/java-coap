/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import org.mbed.coap.CoapMessage;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import java.net.InetSocketAddress;

/**
 *
 * @author szymon
 */
public class RequestBuilder {

    private final StubCoapServer stubResource;
    private final CoapPacket req;

    public RequestBuilder(StubCoapServer stubResource, int port, String uriPath) {
        this.stubResource = stubResource;
        this.req = new CoapPacket(Method.GET, MessageType.Confirmable, uriPath, new InetSocketAddress("127.0.0.1", port));  //NOPMD
    }

    private CoapMessage send() throws CoapException {
        return stubResource.makeRequest(req);
    }

    public RequestBuilder contentFormat(short contentFormat) {
        req.headers().setContentFormat(contentFormat);
        return this;
    }

    public RequestBuilder token(long token) {
        req.setToken(HeaderOptions.convertVariableUInt(token));
        return this;
    }

    public RequestBuilder token(byte[] token) {
        req.setToken(token);
        return this;
    }

    public RequestBuilder query(String queryParameters) {
        req.headers().setUriQuery(queryParameters);
        return this;
    }

    public RequestBuilder non() {
        req.setMessageType(MessageType.NonConfirmable);
        return this;
    }

    public CoapMessage observation(int obs, String body) throws CoapException {
        req.headers().setObserve(obs);
        req.setMethod(null);
        req.setCode(Code.C205_CONTENT);
        req.setPayload(body);
        return send();
    }

    public CoapMessage observation(int obs, byte[] body) throws CoapException {
        req.headers().setObserve(obs);
        req.setMethod(null);
        req.setCode(Code.C205_CONTENT);
        req.setPayload(body);
        return send();
    }

    public CoapMessage observation(int obs, Code respCode) throws CoapException {
        req.headers().setObserve(obs);
        req.setMethod(null);
        req.setCode(respCode);
        return send();
    }

}
