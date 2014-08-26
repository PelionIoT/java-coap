/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import java.util.HashMap;
import java.util.Map;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapHandler;

/**
 *
 * @author szymon
 */
public class CoapResourceMock implements CoapHandler {

    private final Map<MockRequest, CoapPacket> resultMap = new HashMap<>();
    private CoapPacket latestRequest;

    /**
     * Sets result for specified method.
     *
     * @param method
     * @param coapPacket
     */
    public void mockResponse(Method method, CoapPacket coapPacket) {
        resultMap.put(new MockRequest(method, null), coapPacket);
    }

    public void mockResponse(Method method, String uriPath, CoapPacket coapPacket) {
        resultMap.put(new MockRequest(method, uriPath), coapPacket);
    }

    @Override
    public synchronized void handle(CoapExchange exchange) throws CoapException {
        latestRequest = exchange.getRequest();
        CoapPacket resp = resultMap.get(new MockRequest(latestRequest.getMethod(), latestRequest.headers().getUriPath()));
        if (resp == null) {
            resp = new CoapPacket(Code.C400_BAD_REQUEST, MessageType.Confirmable, null);
        }
        exchange.setResponse(resp);
        exchange.sendResponse();
        this.notifyAll();
    }

    public synchronized CoapPacket getLatestRequest() {
        return latestRequest;
    }

    private static class MockRequest {

        private final String uriPath;
        private final Method method;

        public MockRequest(Method method, String uriPath) {
            this.uriPath = uriPath;
            this.method = method;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            //hash = 31 * hash + (this.uriPath != null ? this.uriPath.hashCode() : 0);
            hash = 31 * hash + (this.method != null ? this.method.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MockRequest other = (MockRequest) obj;
            if (this.method != other.method) {
                return false;
            }
            if (this.uriPath == null || other.uriPath == null) {
                return true;
            }
            if (!this.uriPath.equals(other.uriPath)) {
                return false;
            }
            return true;
        }
    }
}
