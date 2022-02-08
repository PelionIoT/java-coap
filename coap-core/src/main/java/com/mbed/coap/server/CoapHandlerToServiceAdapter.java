/**
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapHandlerToServiceAdapter implements CoapHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(CoapHandlerToServiceAdapter.class);
    private final Service<CoapRequest, CoapResponse> service;

    public CoapHandlerToServiceAdapter(Service<CoapRequest, CoapResponse> service) {
        this.service = service;
    }

    @Override
    public void handle(CoapExchange exchange) throws CoapException {
        CoapRequest request = exchange.getRequest().toCoapRequest(exchange.getRequestTransportContext());

        service
                .apply(request)
                .exceptionally(this::rescue)
                .thenAccept(resp -> {
                    exchange.setResponse(exchange.getRequest().createResponseFrom(resp));
                    exchange.sendResponse();
                });
    }


    private CoapResponse rescue(Throwable ex) {
        if (ex instanceof CoapCodeException) {
            return ((CoapCodeException) ex).toResponse();
        }

        LOGGER.warn("Unexpected exception: " + ex.getMessage(), ex);
        return CoapResponse.of(Code.C500_INTERNAL_SERVER_ERROR);
    }

}
