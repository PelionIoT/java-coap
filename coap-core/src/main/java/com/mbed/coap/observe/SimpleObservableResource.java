/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.observe;

import static com.mbed.coap.CoapConstants.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;

/**
 * @author szymon
 */
public class SimpleObservableResource extends AbstractObservableResource {

    private byte[] body;

    public SimpleObservableResource(String body, CoapServer coapServer) {
        super(coapServer);
        this.body = body.getBytes(DEFAULT_CHARSET);
    }

    public SimpleObservableResource(String body, CoapServer coapServer, boolean includeObservableFlag) {
        super(coapServer, includeObservableFlag);
        this.body = body.getBytes(DEFAULT_CHARSET);
    }

    @Override
    public void get(CoapExchange exchange) throws CoapCodeException {
        exchange.setResponseBody(body);
        exchange.setResponseCode(Code.C205_CONTENT);
        exchange.sendResponse();
    }

    /**
     * Changes body for this resource, sends notification to all subscribers.
     *
     * @param body new payload
     * @throws CoapException coap exception
     */
    public void setBody(String body) throws CoapException {
        this.body = body.getBytes(DEFAULT_CHARSET);
        notifyChange(body.getBytes(DEFAULT_CHARSET), null);
    }

    /**
     * Changes body for this resource, sends notification to all subscribers.
     *
     * @param body new payload in bytes
     * @throws CoapException coap exception
     */
    public void setBody(byte[] body) throws CoapException {
        this.body = body;

        notifyChange(body, null);
    }

    public void setBody(String body, NotificationDeliveryListener deliveryListener) throws CoapException {
        this.body = body.getBytes(DEFAULT_CHARSET);
        notifyChange(body.getBytes(DEFAULT_CHARSET), null, null, null, deliveryListener);
    }

    public void setBody(byte[] body, NotificationDeliveryListener deliveryListener) throws CoapException {
        this.body = body;
        notifyChange(body, null, null, null, deliveryListener);
    }

    public String getBody() {
        return new String(body, DEFAULT_CHARSET);
    }

    public byte[] getBodyBytes() {
        return body;
    }

    public void setConfirmNotification(boolean confirmNotification) {
        this.setConNotifications(confirmNotification);
    }

    public void terminateObservations(Code code) throws CoapException {
        this.notifyTermination(code);
    }

    public int getObservationsAmount() {
        return this.obsRelations.size();
    }
}
