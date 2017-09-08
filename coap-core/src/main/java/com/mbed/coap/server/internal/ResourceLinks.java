/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.utils.CoapResource;
import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author szymon
 */
public class ResourceLinks extends CoapResource {

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
