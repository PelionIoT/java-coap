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
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.TransportContext;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;


public interface CoapExchange {

    CoapPacket getRequest();

    CoapPacket getResponse();


    default Opaque getRequestBody() {
        return getRequest().getPayload();
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

    TransportContext getResponseTransportContext();

    /**
     * Retrieves full notification payload. Applies only when handling notification with block2.
     *
     * @param uriPath uri-path
     * @throws CoapException coap exception
     */
    CompletableFuture<CoapPacket> retrieveNotificationBlocks(final String uriPath) throws CoapException;

}
