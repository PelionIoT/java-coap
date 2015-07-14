/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.Code;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;

/**
 * @author szymon
 */
public class SimpleObservableResource extends AbstractObservableResource {

    private String body;

    public SimpleObservableResource(String body, CoapServer coapServer) {
        super(coapServer);
        this.body = body;
    }

    public SimpleObservableResource(String body, CoapServer coapServer, boolean includeObservableFlag) {
        super(coapServer, includeObservableFlag);
        this.body = body;
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
        this.body = body;
        notifyChange(body.getBytes(), null);
    }

    public void setBody(String body, NotificationDeliveryListener deliveryListener) throws CoapException {
        this.body = body;
        notifyChange(body.getBytes(), null, null, null, deliveryListener);
    }

    public String getBody() {
        return body;
    }

    public void setConfirmNotification(boolean confirmNotification) {
        this.setConNotifications(confirmNotification);
    }

    public void terminateObservations() throws CoapException {
        this.notifyTermination();
    }

    public int getObservationsAmount() {
        return this.obsRelations.size();
    }
}
