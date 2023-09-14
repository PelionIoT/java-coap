/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
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
package com.mbed.coap.utils;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapHandler;

/**
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
