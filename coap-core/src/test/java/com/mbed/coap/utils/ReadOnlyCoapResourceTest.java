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
package com.mbed.coap.utils;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.InMemoryCoapTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by szymon
 */
public class ReadOnlyCoapResourceTest {

    private CoapServer srv;
    private CoapClient cli;

    @Before
    public void setUp() throws Exception {
        srv = CoapServerBuilder.newCoapServer(new InMemoryCoapTransport(5683)).start();
        cli = CoapClientBuilder.clientFor(InMemoryCoapTransport.createAddress(5683), CoapServerBuilder.newCoapServer(new InMemoryCoapTransport()).start());
    }

    @After
    public void tearDown() throws Exception {
        srv.stop();
    }

    @Test
    public void test() throws Exception {
        srv.addRequestHandler("/test", new ReadOnlyCoapResource("11"));

        assertEquals("11", cli.resource("/test").get().get().getPayloadString());

        srv.addRequestHandler("/test2", new ReadOnlyCoapResource("22", "", 61));
        assertEquals("22", cli.resource("/test2").get().get().getPayloadString());
        assertEquals(61, cli.resource("/test2").get().get().headers().getMaxAgeValue());

        srv.addRequestHandler("/test3", new ReadOnlyCoapResource("33", "", MediaTypes.CT_TEXT_PLAIN, -1));
        assertEquals("33", cli.resource("/test3").get().get().getPayloadString());
        assertEquals(MediaTypes.CT_TEXT_PLAIN, cli.resource("/test3").get().get().headers().getContentFormat().shortValue());
    }

    @Test
    public void changeValue() throws Exception {
        ReadOnlyCoapResource res = new ReadOnlyCoapResource("01");
        srv.addRequestHandler("/test4", res);

        assertEquals("01", cli.resource("/test4").get().get().getPayloadString());

        res.setResourceBody("02");
        assertEquals("02", cli.resource("/test4").get().get().getPayloadString());
    }
}
