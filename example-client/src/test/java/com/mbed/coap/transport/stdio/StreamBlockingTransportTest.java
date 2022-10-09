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
package com.mbed.coap.transport.stdio;

import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilderForTcp;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilderForTcp;
import com.mbed.coap.transport.javassl.CoapSerializer;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;


public class StreamBlockingTransportTest {

    private final InetSocketAddress adr = new InetSocketAddress("localhost", 5683);

    @Test
    public void clientServerTest() throws Exception {
        //given, server and client linked streams
        PipedOutputStream serverOut = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        serverOut.connect(clientIn);
        clientOut.connect(serverIn);

        CoapServer server = CoapServerBuilderForTcp.newBuilderForTcp().transport(new StreamBlockingTransport(serverOut, serverIn, adr, CoapSerializer.TCP)).build();
        server.start();

        CoapClient client = CoapClientBuilderForTcp
                .newBuilderForTcp(adr)
                .transport(new StreamBlockingTransport(clientOut, clientIn, adr, CoapSerializer.TCP))
                .build();

        assertNotNull(client.ping().get());
        server.stop();
        client.close();
    }

    @Test
    public void shouldStopWhenMalformedCoapReceived() throws IOException, InterruptedException {
        PipedOutputStream clientOut = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(clientIn);

        //given
        StreamBlockingTransport streamBlockingTransport = new StreamBlockingTransport(clientOut, clientIn, adr, CoapSerializer.UDP);
        streamBlockingTransport.start();

        await().until(streamBlockingTransport::isRunning);

        //when
        outputStream.write("123456".getBytes());
        outputStream.flush();

        //then
        streamBlockingTransport.receive();

        await().until(() -> !streamBlockingTransport.isRunning());
        assertFalse(streamBlockingTransport.isRunning());
    }
}