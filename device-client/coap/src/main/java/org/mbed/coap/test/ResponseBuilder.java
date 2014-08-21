/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;

/**
 *
 * @author szymon
 */
public class ResponseBuilder {

    private final StubCoapServer stubResource;
    private final CoapPacket req;
    private final StubCoapServer.StubResponse resp;

    ResponseBuilder(StubCoapServer stubResource, CoapPacket req, StubCoapServer.StubResponse resp) {
        this.req = req;
        this.resp = resp;
        this.stubResource = stubResource;
    }

    public void retrn(Code responseCode) {
        resp.resp.setCode(responseCode);
        stubResource.add(req, resp);
    }

    public void retrn() {
        stubResource.add(req, resp);
    }

    public void retrn(String body) {
        resp.resp.setPayload(body);
        stubResource.add(req, resp);
    }

    public void retrn(byte[] body) {
        resp.resp.setPayload(body);
        stubResource.add(req, resp);
    }

    public ResponseBuilder withMaxAge(long maxAge) {
        resp.resp.headers().setMaxAge(maxAge);
        return this;
    }

    public ResponseBuilder withContentFormat(short contentFormat) {
        resp.resp.headers().setContentFormat(contentFormat);
        return this;
    }

    public ResponseBuilder obs(int observe) {
        resp.resp.headers().setObserve(observe);
        return this;
    }
}
