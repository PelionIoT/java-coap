/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;

/**
 *
 * @author szymon
 */
public class StubResourceBuilder {

    private final StubCoapServer stubResource;
    private final CoapPacket req = new CoapPacket();
    private final CoapPacket resp = new CoapPacket(Code.C205_CONTENT, MessageType.Acknowledgement, null);
    private boolean delayNotif;
    private long delayMilli;

    public StubResourceBuilder(StubCoapServer stubResource, String uriPath, Method method) {
        this.stubResource = stubResource;
        req.setMethod(method);
        req.headers().setUriPath(uriPath);
        switch (method) {
            case GET:
                resp.setCode(Code.C205_CONTENT);
                break;
            case DELETE:
                resp.setCode(Code.C202_DELETED);
                break;
            case POST:
                resp.setCode(Code.C201_CREATED);
                break;
            case PUT:
                resp.setCode(Code.C204_CHANGED);
                break;
            default:
                throw new RuntimeException();
        }
    }

    public ResponseBuilder then() {
        return new ResponseBuilder(stubResource, req, new StubCoapServer.StubResponse(resp, delayMilli, delayNotif));
    }

    public void thenReturn(Code responseCode) {
        resp.setCode(responseCode);
        stubResource.add(req, new StubCoapServer.StubResponse(resp, delayMilli, delayNotif));
    }

    public void thenReturn() {
        stubResource.add(req, new StubCoapServer.StubResponse(resp, delayMilli, delayNotif));
    }

    public void thenReturn(String body) {
        resp.setPayload(body);
        stubResource.add(req, new StubCoapServer.StubResponse(resp, delayMilli, delayNotif));
    }

    public StubResourceBuilder withContentFormat(short contentFormat) {
        req.headers().setContentFormat(contentFormat);
        return this;
    }

    public StubResourceBuilder withMaxAge(long maxAge) {
        req.headers().setMaxAge(maxAge);
        return this;
    }

    public StubResourceBuilder withObserve() {
        req.headers().setObserve(StubCoapServer.ANY_INTEGER);
        return this;
    }

    public StubResourceBuilder withBody(String body) {
        req.setPayload(body.getBytes());
        return this;
    }

    public StubResourceBuilder withBody(byte[] body) {
        req.setPayload(body);
        return this;
    }

    public StubResourceBuilder withBody() {
        req.setPayload(StubCoapServer.ANY_BYTEARR);
        return this;
    }

    public StubResourceBuilder delayNotif() {
        delayNotif = true;
        return this;
    }

    public StubResourceBuilder delay(long delayMilli) {
        this.delayMilli = delayMilli;
        return this;
    }
}
