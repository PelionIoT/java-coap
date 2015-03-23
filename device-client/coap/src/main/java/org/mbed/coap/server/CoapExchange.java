/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Logger;
import org.mbed.coap.packet.BlockOption;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.HeaderOptions;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.DataConvertingUtility;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import org.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
public abstract class CoapExchange {

    private static final Logger LOGGER = Logger.getLogger(CoapExchange.class.getName());
    protected CoapPacket request;
    protected CoapPacket response;
    private boolean isDelayedResponse;

    public CoapExchange(CoapPacket request, CoapPacket response) {
        this.request = request;
        this.response = response;
    }

    public CoapPacket getRequest() {
        return request;
    }

    protected CoapPacket getResponse() {
        return response;
    }

    /**
     * Returns request method (GET, PUT, POST, DELETE)
     *
     * @return method
     */
    public Method getRequestMethod() {
        return request.getMethod();
    }

    /**
     * Returns requested URI path
     *
     * @return uri path
     */
    public String getRequestUri() {
        return request.headers().getUriPath();
    }

    /**
     * Returns request coap headers
     * @return request headers
     */
    public HeaderOptions getRequestHeaders() {
        return request.headers();
    }

    public byte[] getRequestBody() {
        return request.getPayload();
    }

    /**
     * Returns request body
     * @return payload body as string
     */
    public String getRequestBodyString() {
        return request.getPayloadString();
    }

    /**
     * Returns source address of incoming message
     * @return remote address
     */
    public abstract InetSocketAddress getRemoteAddress();

    /**
     * Returns response headers that will be sent to requester
     * @return response headers
     */
    public HeaderOptions getResponseHeaders() {
        return response.headers();
    }

    public abstract void setResponseBody(byte[] payload);

    /**
     * Sets response content type of a body
     * @param contentType response content type
     */
    public abstract void setResponseContentType(short contentType);

    public abstract void setResponseToken(byte[] token);

    public abstract byte[] getResponseToken();

    /**
     * Sets response body
     *
     * @param body response body
     */
    public void setResponseBody(String body) {
        setResponseBody(DataConvertingUtility.encodeString(body));
    }

    /**
     * Sets response CoAP code
     * @param code CoAP code
     */
    public abstract void setResponseCode(Code code);

    public abstract void setResponse(CoapPacket message);

    /**
     * Sends CoAP reset response
     */
    public void sendResetResponse() {
        response = request.createResponse();
        response.setMessageType(MessageType.Reset);
        response.setCode(null);
        this.getCoapServer().sendResponse(this);
        response = null;
        //request = null;
    }
    //private CoapPacket oldResp = null;

    /**
     * Sends response, this method must be called only once at the end of
     * request handling. No operations are allowed on this object after.
     */
    public void sendResponse() {
        if (!isDelayedResponse) {
            if (request.getMessageType() == MessageType.NonConfirmable && request.getMethod() == null) {
                LOGGER.finest("Send response ignored for NON response");
            } else {
                send();
            }
            response = null;
        } else {
            try {
                this.getCoapServer().makeRequest(response, Callback.IGNORE);
            } catch (CoapException ex) {
                LOGGER.warning("Error while sending delayed response: " + ex.getMessage());
            }
        }
    }

    public abstract CoapServer getCoapServer();

    /**
     * Sends empty ACK to server telling that response will come later on. If
     * request wan NON, then will not send anything
     */
    public void sendDelayedAck() {
        if (request.getMessageType() == MessageType.NonConfirmable) {
            return;
        }
        CoapPacket emptyAck = new CoapPacket(null);
        emptyAck.setCode(null);
        emptyAck.setMethod(null);
        emptyAck.setMessageType(MessageType.Acknowledgement);
        emptyAck.setMessageId(getRequest().getMessageId());

        CoapPacket tmpResp = this.getResponse();
        this.setResponse(emptyAck);

        //this.send();
        this.getCoapServer().sendResponse(this);

        this.setResponse(tmpResp);
        tmpResp.setMessageType(MessageType.Confirmable);
        isDelayedResponse = true;
    }

    protected abstract void send();

    /**
     * Returns request transport context.
     *
     * @return transport context or null if does not exist.
     */
    public abstract TransportContext getRequestTransportContext();

    public abstract TransportContext getResponseTransportContext();

    public abstract void setResponseTransportContext(TransportContext responseTransportContext);

    /**
     * Retrieves full notification payload. Applies only when handling notification with block2.
     *
     * @param uriPath uri-path
     * @param callback callback
     * @throws CoapException coap exception
     */
    public void retrieveNotificationBlocks(final String uriPath, final Callback<CoapPacket> callback) throws CoapException {
        if (request.headers().getObserve() == null || request.headers().getBlock2Res() == null) {
            throw new IllegalStateException("Method retrieveNotificationBlocks can be called only when received notification with block header.");
        }
        //get all blocks
        CoapPacket fullNotifRequest = new CoapPacket(Method.GET, MessageType.Confirmable, uriPath, getRemoteAddress());
        fullNotifRequest.headers().setBlock2Res(new BlockOption(1, request.headers().getBlock2Res().getSzx(), false));
        final byte[] etag = request.headers().getEtag();

        getCoapServer().makeRequest(fullNotifRequest, new Callback<CoapPacket>() {
            @Override
            public void callException(Exception ex) {
                callback.callException(ex);
            }

            @Override
            public void call(CoapPacket coapPacket) {
                if (coapPacket.getCode() == Code.C205_CONTENT) {

                    try (ByteArrayBackedOutputStream bytesOut = new ByteArrayBackedOutputStream(request.getPayload().length + coapPacket.getPayload().length)) {
                        bytesOut.write(request.getPayload(), 0, request.getPayload().length);
                        bytesOut.write(coapPacket.getPayload(), 0, coapPacket.getPayload().length);
                        coapPacket.setPayload(bytesOut.toByteArray());
                    }

                    if (Arrays.equals(etag, coapPacket.headers().getEtag())) {
                        callback.call(coapPacket);
                    } else {
                        callException(new CoapException("Could not retrieve full observation message, etag does not mach [" + getRemoteAddress() + uriPath + "]"));
                    }
                } else {
                    callException(new CoapCodeException(coapPacket.getCode(), "Unexpected response when retrieving full observation message [" + getRemoteAddress() + uriPath + "]"));
                }
            }
        });
    }

    @Override
    public String toString() {
        return "CoapExchange [" + "request=" + request + ", response=" + response + ']';
    }
}
