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
package com.mbed.coap.example;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.utils.CoapResource;
import java.text.ParseException;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by szymon
 */
public class DeviceEmulatorTest {

    CoapServer stubServer;
    Consumer<String> registered = Mockito.mock(Consumer.class);


    @Before
    public void setUp() throws Exception {

        DatagramSocketTransport transport = new DatagramSocketTransport(0);
        stubServer = CoapServerBuilder.newCoapServer(transport);

        stubServer.addRequestHandler("/rd", new CoapResource() {
            @Override
            public void get(CoapExchange exchange) throws CoapCodeException {
                throw new CoapCodeException(Code.C405_METHOD_NOT_ALLOWED);
            }

            @Override
            public void post(CoapExchange exchange) throws CoapCodeException {
                String epName;
                try {
                    epName = exchange.getRequest().headers().getUriQueryMap().get("ep");
                } catch (ParseException e) {
                    throw new CoapCodeException(Code.C400_BAD_REQUEST);
                }

                CoapPacket resp = exchange.getResponse();
                resp.setCode(Code.C201_CREATED);
                resp.headers().setLocationPath("/rd/" + epName);
                exchange.sendResponse();
                registered.accept(epName);
            }
        });

        stubServer.start();
    }

    @After
    public void tearDown() throws Exception {
        stubServer.stop();
    }

    @Test
    public void registerEmulator() throws Exception {
        final int port = stubServer.getLocalSocketAddress().getPort();

        DeviceEmulator deviceEmulator = new DeviceEmulator(String.format("coap://localhost:%d/rd?ep=dev123&lt=60", port), null);


        verify(registered, timeout(10000)).accept(eq("dev123"));
        assertTrue(deviceEmulator.getRegistrationManager().isRegistered());
    }
}