/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.linkformat.LinkFormatBuilder;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MediaTypes;
import org.mbed.coap.utils.CoapResource;

/**
 * @author szymon
 */
class ResourceLinks extends CoapResource {

    private final CoapServer server;

    public ResourceLinks(final CoapServer server) {
        this.server = server;
        this.getLink().setContentType(MediaTypes.CT_APPLICATION_LINK__FORMAT);
    }

    @Override
    public void get(CoapExchange ex) throws CoapCodeException {
        Map<String, String> linkQuery = null;
        try {
            linkQuery = ex.getRequestHeaders().getUriQueryMap();
        } catch (ParseException expn) {
            throw new CoapCodeException(Code.C400_BAD_REQUEST, expn);
        }

        //String filter = linkQuery != null ? linkQuery.get("rt") : null;
        List<LinkFormat> links = server.getResourceLinks();

        //filter links
        links = LinkFormatBuilder.filter(links, linkQuery);

        //sort
        Collections.sort(links, new Comparator<LinkFormat>() {
            @Override
            public int compare(LinkFormat o1, LinkFormat o2) {
                return o1.getUri().compareTo(o2.getUri());
            }
        });

        String resources = LinkFormatBuilder.toString(links);
        ex.setResponseCode(Code.C205_CONTENT);
        ex.getResponseHeaders().setContentFormat(MediaTypes.CT_APPLICATION_LINK__FORMAT);
        ex.setResponseBody(resources.getBytes());
        ex.sendResponse();
    }
}
