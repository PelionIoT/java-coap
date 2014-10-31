/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.transport.TransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
class CoapExchangeImpl extends CoapExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapExchangeImpl.class);
    private CoapServer coapServer;
    private TransportContext requestTransportContext;
    private TransportContext responseTransportContext = TransportContext.NULL;

    public CoapExchangeImpl(CoapPacket request, CoapServer coapServer) {
        this(request, coapServer, null);
    }

    public CoapExchangeImpl(CoapPacket request, CoapServer coapServer, TransportContext transportContext) {
        super(request, request.createResponse());
        //        if (LOGGER.isWarnEnabled() && this.response == null) {
        //            LOGGER.warn("CoapExchangeImpl() response is null");
        //        }
        this.coapServer = coapServer;
        this.requestTransportContext = transportContext;
    }

    @Override
    public TransportContext getRequestTransportContext() {
        return requestTransportContext;
    }

    @Override
    public TransportContext getResponseTransportContext() {
        return responseTransportContext;
    }

    @Override
    public void setResponseTransportContext(TransportContext responseTransportContext) {
        this.responseTransportContext = responseTransportContext;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return getRequest().getRemoteAddress();
    }

    @Override
    public void setResponseBody(byte[] payload) {
        getResponse().setPayload(payload);
    }

    @Override
    public void setResponseCode(Code code) {
        getResponse().setCode(code);
    }

    @Override
    public void setResponseContentType(short contentType) {
        getResponse().headers().setContentFormat(contentType);
    }

    @Override
    public void setResponse(CoapPacket message) {
        if (this.response != null) {
            message.setMessageId(this.response.getMessageId());
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Coap messaging: trying to set response for request with type:" + this.getRequest().getMessageType());
            }
        }
        this.response = message;
    }

    @Override
    protected void send() {
        coapServer.sendResponse(this);
    }

    @Override
    public String toString() {
        return "CoapExchangeImpl{" + super.toString() + '}';
    }

    @Override
    public CoapServer getCoapServer() {
        return coapServer;
    }

    @Override
    public void setResponseToken(byte[] token) {
        getResponse().setToken(token);
    }

    @Override
    public byte[] getResponseToken() {
        return getResponse().getToken();
    }
}
