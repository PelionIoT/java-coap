/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import org.mbed.coap.packet.Code;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.server.CoapExchange;

/**
 *
 * @author szymon
 */
public class SimpleCoapResource extends CoapResource {

    private String resourceBody;
    private Integer maxAgeSeconds;
    private Short contentType = 0;

    public void setResourceBody(String resourceBody) {
        this.resourceBody = resourceBody;
    }

    public SimpleCoapResource(String body) {
        this(body, null, null, -1);
    }

    public SimpleCoapResource(String body, String resourceType, int maxAgeSeconds) {
        this(body, resourceType, null, maxAgeSeconds);
    }

    public SimpleCoapResource(String body, String resourceType) {
        this(body, resourceType, null, -1);
    }

//    public SimpleCoapResource(String body, String resourceType, Short contentType) {
//        this(body, resourceType, contentType, -1);
//    }
    public SimpleCoapResource(String body, String resourceType, Short contentType, int maxAgeSeconds) {
        this.resourceBody = body;
        if (maxAgeSeconds >= 0) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
        this.getLink().setContentType(contentType);
        if (resourceType != null) {
            this.getLink().setResourceType(resourceType);
        }
        this.contentType = contentType;
    }

    @Override
    public void get(CoapExchange ex) throws CoapCodeException {
        ex.setResponseBody(resourceBody);
        ex.setResponseCode(Code.C205_CONTENT);
        if (contentType != null) {
            ex.setResponseContentType(contentType);
        }
        if (maxAgeSeconds != null && maxAgeSeconds >= 0) {
            ex.getResponseHeaders().setMaxAge((long) maxAgeSeconds);
        }
        ex.sendResponse();
    }
}
