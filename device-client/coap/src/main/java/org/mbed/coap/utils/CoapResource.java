/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import org.mbed.coap.packet.Code;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapHandler;

/**
 *
 * @author szymon
 */
public abstract class CoapResource implements CoapHandler {

    protected LinkFormat link = new LinkFormat(null);

    protected CoapResource() {
        // restricted instantiation rights
    }

    public LinkFormat getLink() {
        return link;
    }

    @Override
    public void handle(CoapExchange exchange) throws CoapException {
        switch (exchange.getRequestMethod()) {
            case POST:
                post(exchange);
                break;
            case GET:
                get(exchange);
                break;
            case PUT:
                put(exchange);
                break;
            case DELETE:
                delete(exchange);
                break;
            default:
                throw new RuntimeException();
        }
    }

    public abstract void get(CoapExchange exchange) throws CoapCodeException;

    public void put(@SuppressWarnings("unused") CoapExchange exchange) throws CoapCodeException {
        throw new CoapCodeException(Code.C405_METHOD_NOT_ALLOWED);
    }

    public void delete(@SuppressWarnings("unused") CoapExchange exchange) throws CoapCodeException {
        throw new CoapCodeException(Code.C405_METHOD_NOT_ALLOWED);
    }

    public void post(@SuppressWarnings("unused") CoapExchange exchange) throws CoapCodeException {
        throw new CoapCodeException(Code.C405_METHOD_NOT_ALLOWED);
    }

}
