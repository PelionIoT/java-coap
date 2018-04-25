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
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import com.mbed.coap.utils.HexArray;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author szymon
 */
public class MalformedPacketTest {

    private CoapServer server = null;
    private int serverPort;

    @Before
    public void setUp() throws IOException {
        server = CoapServer.builder().transport(0).build();
        server.addRequestHandler("/test/1", new ReadOnlyCoapResource("Dziala"));
        server.addRequestHandler("/.well-known/core", new ReadOnlyCoapResource(""));
        server.start();

        serverPort = server.getLocalSocketAddress().getPort();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void simpleRequest() throws Exception {
        DatagramSocketTransport clientTransport = new DatagramSocketTransport(new InetSocketAddress(0));

        CoapClient cnn = CoapClientBuilder
                .newBuilder(InMemoryCoapTransport.createAddress(serverPort))
                .transport(clientTransport).build();

        //when, malformed packet
        String malformedPacket1 = "42021ed720d468bfb0daed7d8da264e29198e37bc4817da81c86b66f2d66276421a4769789b5ab5b1bb09e9300aca1b82362c2759dc6c8d6c87441b012933677704beeab571d3d3d95cfedfc9f57dd0fc145fd8d39af24d7109916a53b52cdb593c19600c15e69088f7405afe2dcbb5fed1ee17b5bd216c7a654f8ab674e24922cd1b57c0c1a626b05e9032cd25e70dbb5383e1b222c930990962c80c4a21ae3148f25d01670aa33444fe4cc6abe904a1b4950c3182e4679242858f8e71ead0d0e5c25f11643c9375f5ec57633cf3fb088d8a4c36c2ecfad042ca33bf4be795fd3b7da341c42ce7b5c70468d8ece307140705285e974a077f0a465785904ecdf0c3a99284fed82c258a490764b1c075dd9fd02577a80f929fcad30395e78b1da4c961f7a39b0c5d5dd8a567bd5abb22df8a3288a2ab9f82aea6e79bfd560baee12294a4611f529096f89d2cffd764463f317b481ba4d7fd6f1d1b40e6dffd6fd0aae724aeb802185f5daaa66645745b46dcf2a09511862b1e852b819527a901bb1662805dc553e2ade9274b41f825d036cbd975ac313f23a9f7d1ec8deb7b2658b915204d6f23e2edf18c782c15a6ce37f67771809a2b298c6270255d0ebe98a69809f75bce7df1b10584604bee1bcdf6758f6210b0cb9163187b4d518d94d84799c3453dd0204b37b214a242e3cb4be522ff0c3b09e96cb37242d4d65779c88590e1438b74a350213346707673c9fd33b39221115e1479d6dd70e787ae2ba612dd40b4ee856e27856ac09f87d0e1bb2d8091e1f6ca343f2f0f8";
        String malformedPacket2 = "4904da1f1b4f54306867554f75b56c61726765ff2584cbed7396e29c7b73b07d173480816dfcf97b08ecb20bb3e3347561c81a3e42afca9e2004ddb905123d3038727599af09bd642647cca94bd09b2daed91bd7096bb1c32244b5052f3349caa9243a2f741f33da320d9142af8d00e662ae673e685df911e5811e352863dd303a3320520c20a26e706f9ffd1ceda579b3a1ca912906d1be2334e1752783d9c927f4bec8cd1c7d9d8095f52db5666b1fbef03b2c1666f183cdc59d5276f8175a8c55bb936663a0e85a1d2d1428bb449ce78447c8700ca1060c61a05330cd5b6daddebe287a3a8aee107da3564d6d26e03c05b8ced83608fbc4363343010b5c67d0e5672ea31d63c9c24061865f9682c50c5f0f0a5ac26c9880b55d6cdbb7e7bc06f551376e21fba5b4ec1c28ff2463a8f572054f09852d18900c6de51b7f";
        String malformedPacket3 = "610284cc3fbb2e77656c6c2d6b6e6f776e04636f7265";
        String malformedPacket4 = "4aab";

        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.send(new DatagramPacket(HexArray.fromHex(malformedPacket1), 0, malformedPacket1.length() / 2, InMemoryCoapTransport.createAddress(serverPort)));
        datagramSocket.send(new DatagramPacket(HexArray.fromHex(malformedPacket2), 0, malformedPacket2.length() / 2, InMemoryCoapTransport.createAddress(serverPort)));
        datagramSocket.send(new DatagramPacket(HexArray.fromHex(malformedPacket3), 0, malformedPacket3.length() / 2, InMemoryCoapTransport.createAddress(serverPort)));
        datagramSocket.send(new DatagramPacket(HexArray.fromHex(malformedPacket4), 0, malformedPacket4.length() / 2, InMemoryCoapTransport.createAddress(serverPort)));


        //then, server should keep working
        assertEquals("Dziala", cnn.resource("/test/1").get().get().getPayloadString());

        cnn.close();
    }

}
