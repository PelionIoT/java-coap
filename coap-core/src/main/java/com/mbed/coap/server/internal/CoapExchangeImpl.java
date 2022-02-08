/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.server.internal;

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.TransportContext;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public class CoapExchangeImpl implements CoapExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapExchangeImpl.class.getName());
    private final CoapServer coapServer;
    private final TransportContext requestTransportContext;
    private final TransportContext responseTransportContext = TransportContext.NULL;
    protected CoapPacket request;
    protected CoapPacket response;

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
    public void setResponse(CoapPacket message) {
        if (this.response != null && response.getMessageId() != -1) {
            message.setMessageId(this.response.getMessageId());
        } else {
            LOGGER.debug("Coap messaging: trying to set response for request with type:" + this.getRequest().getMessageType());
        }
        this.response = message;
    }

    @Override
    public void sendResetResponse() {
        response = request.createResponse();
        response.setMessageType(MessageType.Reset);
        response.setCode(null);
        coapServer.sendResponse(this);
        response = null;
    }

    @Override
    public void sendResponse() {
        if (request.getMessageType() == MessageType.NonConfirmable && request.getMethod() == null) {
            LOGGER.trace("Send response ignored for NON response");
        } else {
            send();
        }
        response = null;
    }


    protected void send() {
        coapServer.sendResponse(this);
    }

    @Override
    public String toString() {
        return "CoapExchange [" + "request=" + request + ", response=" + response + ']';
    }


    @Override
    public CompletableFuture<CoapPacket> retrieveNotificationBlocks(final String uriPath) throws CoapException {
        if (request.headers().getObserve() == null || request.headers().getBlock2Res() == null) {
            throw new IllegalStateException("Method retrieveNotificationBlocks can be called only when received notification with block header.");
        }
        //get all blocks
        CoapPacket fullNotifRequest = new CoapPacket(Method.GET, MessageType.Confirmable, uriPath, getRemoteAddress());
        fullNotifRequest.headers().setBlock2Res(new BlockOption(1, request.headers().getBlock2Res().getBlockSize(), false));
        final Opaque etag = request.headers().getEtag();

        return coapServer
                .makeRequest(fullNotifRequest)
                .thenCompose(coapPacket -> {
                            if (coapPacket.getCode() == Code.C205_CONTENT) {
                                coapPacket.setPayload(request.getPayload().concat(coapPacket.getPayload()));
                                if (Objects.equals(etag, coapPacket.headers().getEtag())) {
                                    return completedFuture(coapPacket);
                                } else {
                                    return failedFuture(new CoapException("Could not retrieve full observation message, etag does not mach [" + getRemoteAddress() + uriPath + "]"));
                                }
                            }
                            return failedFuture(new CoapCodeException(coapPacket.getCode(), "Unexpected response when retrieving full observation message [" + getRemoteAddress() + uriPath + "]"));
                        }
                );

    }

}
