/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.util.Arrays;
import java.util.logging.Logger;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.BlockOption;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import org.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
public class CoapExchangeImpl implements CoapExchange {

    private static final Logger LOGGER = Logger.getLogger(CoapExchangeImpl.class.getName());
    private CoapServer coapServer;
    private TransportContext requestTransportContext;
    private TransportContext responseTransportContext = TransportContext.NULL;
    protected CoapPacket request;
    protected CoapPacket response;
    private boolean isDelayedResponse;

    public CoapExchangeImpl(CoapPacket request, CoapServer coapServer) {
        this(request, coapServer, null);
    }

    public CoapExchangeImpl(CoapPacket request, CoapServer coapServer, TransportContext transportContext) {
        this.request = request;
        this.response = request.createResponse();
        this.coapServer = coapServer;
        this.requestTransportContext = transportContext;
    }

    @Override
    public CoapPacket getRequest() {
        return request;
    }

    @Override
    public CoapPacket getResponse() {
        return response;
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
    public void setResponse(CoapPacket message) {
        if (this.response != null) {
            message.setMessageId(this.response.getMessageId());
        } else {
            LOGGER.fine("Coap messaging: trying to set response for request with type:" + this.getRequest().getMessageType());
        }
        this.response = message;
    }

    @Override
    public void sendResetResponse() {
        response = request.createResponse();
        response.setMessageType(MessageType.Reset);
        response.setCode(null);
        this.getCoapServer().sendResponse(this);
        response = null;
    }

    @Override
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
                this.getCoapServer().makeRequest(response, Callback.ignore());
            } catch (CoapException ex) {
                LOGGER.warning("Error while sending delayed response: " + ex.getMessage());
            }
        }
    }

    @Override
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


    protected void send() {
        coapServer.sendResponse(this);
    }

    @Override
    public String toString() {
        return "CoapExchange [" + "request=" + request + ", response=" + response + ']';
    }

    @Override
    public CoapServer getCoapServer() {
        return coapServer;
    }

    @Override
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


}
