/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.utils;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;

/**
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
        if (maxAgeSeconds != null) {
            ex.getResponseHeaders().setMaxAge((long) maxAgeSeconds);
        }
        ex.sendResponse();
    }
}
