/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.HeaderOptions;
import com.mbed.coap.packet.Method;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import java.net.InetSocketAddress;

/**
 * @author szymon
 */
public interface CoapExchange {

    CoapPacket getRequest();

    CoapPacket getResponse();

    /**
     * Returns request method (GET, PUT, POST, DELETE)
     *
     * @return method
     */
    default Method getRequestMethod() {
        return getRequest().getMethod();
    }

    /**
     * Returns requested URI path
     *
     * @return uri path
     */
    default String getRequestUri() {
        return getRequest().headers().getUriPath();
    }

    /**
     * Returns request coap headers
     *
     * @return request headers
     */
    default HeaderOptions getRequestHeaders() {
        return getRequest().headers();
    }

    default byte[] getRequestBody() {
        return getRequest().getPayload();
    }

    /**
     * Returns request body
     *
     * @return payload body as string
     */
    default String getRequestBodyString() {
        return getRequest().getPayloadString();
    }

    /**
     * Returns source address of incoming message
     *
     * @return remote address
     */
    default InetSocketAddress getRemoteAddress() {
        return getRequest().getRemoteAddress();
    }

    /**
     * Returns response headers that will be sent to requester
     *
     * @return response headers
     */
    default HeaderOptions getResponseHeaders() {
        return getResponse().headers();
    }

    default void setResponseBody(byte[] payload) {
        getResponse().setPayload(payload);
    }

    /**
     * Sets response content type of a body
     *
     * @param contentType response content type
     */
    default void setResponseContentType(short contentType) {
        getResponse().headers().setContentFormat(contentType);
    }

    default void setResponseToken(byte[] token) {
        getResponse().setToken(token);
    }

    /**
     * Sets response body
     *
     * @param body response body
     */
    default void setResponseBody(String body) {
        setResponseBody(DataConvertingUtility.encodeString(body));
    }

    /**
     * Sets response CoAP code
     *
     * @param code CoAP code
     */
    default void setResponseCode(Code code) {
        getResponse().setCode(code);
    }

    void setResponse(CoapPacket message);

    /**
     * Sends CoAP reset response
     */
    void sendResetResponse();

    /**
     * Sends response, this method must be called only once at the end of
     * request handling. No operations are allowed on this object after.
     */
    void sendResponse();

    CoapServer getCoapServer();

    /**
     * Sends empty ACK to server telling that response will come later on. If
     * request wan NON, then will not send anything
     */
    void sendDelayedAck();

    /**
     * Returns request transport context.
     *
     * @return transport context or null if does not exist.
     */
    TransportContext getRequestTransportContext();

    TransportContext getResponseTransportContext();

    void setResponseTransportContext(TransportContext responseTransportContext);

    /**
     * Retrieves full notification payload. Applies only when handling notification with block2.
     *
     * @param uriPath uri-path
     * @param callback callback
     * @throws CoapException coap exception
     */
    void retrieveNotificationBlocks(final String uriPath, final Callback<CoapPacket> callback) throws CoapException;

}
