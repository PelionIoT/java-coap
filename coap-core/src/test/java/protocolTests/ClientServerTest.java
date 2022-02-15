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
package protocolTests;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class ClientServerTest {

    private CoapServer server = null;
    private int SERVER_PORT;
    private InetSocketAddress serverAddress;

    @BeforeEach
    public void setUp() throws IOException {
        server = CoapServer.builder().transport(0).build();
        server.addRequestHandler("/test/1", new ReadOnlyCoapResource("Dziala"));
        server.addRequestHandler("/resource*", new ReadOnlyCoapResource("Prefix dziala"));
        server.addRequestHandler("/", new ReadOnlyCoapResource("Shortest path"));
        server.useCriticalOptionTest(false);
        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();
        serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT);
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void simpleRequest() throws Exception {
        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(serverAddress);
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(request);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCustomHeader() throws Exception {
        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(serverAddress);
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put(74, new byte[]{1, 2, 3});

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(request);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCriticalCustomHeader() throws Exception {
        server.useCriticalOptionTest(true);
        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(serverAddress);
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put(71, new byte[]{1, 2, 3});

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(request);
        assertEquals(Code.C402_BAD_OPTION, callback.get().getCode());
        cnn.stop();
    }

    @Test
    public void simpleRequestWithCriticalCustomHeader2() throws Exception {
        server.useCriticalOptionTest(false);
        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(serverAddress);
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.headers().put((byte) 71, new byte[]{1, 2, 3});

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(request);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequestToShortestPath() throws Exception {
        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CoapPacket request = new CoapPacket(serverAddress);
        request.setMethod(Method.GET);
        request.headers().setUriPath("/");
        request.setMessageId(1648);

        assertEquals("Shortest path", cnn.makeRequest(request).join().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequest2() throws Exception {
        try (CoapClient client = CoapClientBuilder.newBuilder().target(SERVER_PORT).build()) {

            Future<CoapPacket> coapResp = client.resource("/test/1").get();
            assertEquals("Dziala", coapResp.get().getPayloadString());
        }
    }

    @Test
    public void simpleRequest3() throws Exception {
        try (CoapClient client = CoapClientBuilder.newBuilder().target(SERVER_PORT).build()) {

            Future<CoapPacket> coapResp = client.resource("/resource/123").get();
            assertEquals("Prefix dziala", coapResp.get().getPayloadString());

            coapResp = client.resource("/test/1/tre").get();
            assertEquals(Code.C404_NOT_FOUND, coapResp.get().getCode());
        }
    }

    @Test
    @Disabled
    public void simpleIPv6Request() throws CoapException, IOException {
        InetAddress adr = InetAddress.getByName("::1");

        CoapServer ipv6Server = CoapServerBuilder.newBuilder().transport(0).build();
        ipv6Server.addRequestHandler("/resource", new ReadOnlyCoapResource("1234qwerty"));
        ipv6Server.start();

        try (CoapClient client = CoapClientBuilder.newBuilder(new InetSocketAddress(adr, ipv6Server.getLocalSocketAddress().getPort())).build()) {

            CoapPacket coapResp = client.resource("/resource").sync().get();
            assertEquals("1234qwerty", coapResp.getPayloadString());

        }
        ipv6Server.stop();
    }

    @Test
    public void duplicateTest() throws Exception {
        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(3000);

        CoapPacket cpRequest = new CoapPacket(Method.GET, MessageType.Confirmable, "/test/1", null);
        cpRequest.setMessageId(4321);
        DatagramPacket packet = new DatagramPacket(cpRequest.toByteArray(), cpRequest.toByteArray().length, InetAddress.getLocalHost(), SERVER_PORT);
        DatagramPacket recPacket = new DatagramPacket(new byte[1024], 1024);
        DatagramPacket recPacket2 = new DatagramPacket(new byte[1024], 1024);
        datagramSocket.send(packet);
        datagramSocket.receive(recPacket);

        //send duplicate
        datagramSocket.send(packet);
        datagramSocket.receive(recPacket2);
        datagramSocket.close();
        assertArrayEquals(recPacket.getData(), recPacket2.getData());

    }

    @Test
    public void requestWithPacketLost() throws CoapException, IOException {
        CoapServer serverNode = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create(5683)).build();
        final ReadOnlyCoapResource res = new ReadOnlyCoapResource("Not dropped");
        serverNode.addRequestHandler("/dropping", res);
        serverNode.start();

        try (CoapClient cnn = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(5683))
                .transport(new DroppingPacketsTransportWrapper(0, (byte) 0) {
                    private boolean hasDropped = false;

                    @Override
                    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
                        //will drop only first packet
                        if (!hasDropped) {
                            hasDropped = true;
                            res.setResourceBody("dropped");
                            System.out.println("dropped");
                        } else {
                            super.sendPacket0(coapPacket, adr, tranContext);
                        }
                    }

                })
                .timeout(new CoapTimeout(100)).build()) {

            CoapPacket resp = cnn.resource("/dropping").sync().get();
            assertEquals("dropped", resp.getPayloadString());
        }
    }

    @Test
    public void simpleRequest4() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(0).build();
        cnn.start();

        CoapClient client = CoapClientBuilder.clientFor(serverAddress, cnn);
        assertEquals("Dziala", client.resource("/test/1").maxAge(2635593050L).get().join().getPayloadString());
        cnn.stop();
    }

    @Test
    public void simpleRequest5() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create(61601)).build();
        srv.addRequestHandler("/temp", new ReadOnlyCoapResource("23 C"));
        srv.start();

        try (CoapClient cnn = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(61601)).transport(InMemoryCoapTransport.create(0)).build()) {
            assertEquals("23 C", cnn.resource("/temp").sync().get().getPayloadString());
        }
        srv.stop();
    }

    @Test
    public void simpleRequestWithUnknownCriticalOptionHeader() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create(61601)).build();
        srv.addRequestHandler("/temp", new ReadOnlyCoapResource("23 C"));
        srv.start();

        try (CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(61601)).transport(new InMemoryCoapTransport()).build()) {
            assertEquals(Code.C402_BAD_OPTION, client.resource("/temp").header(123, "dupa".getBytes()).sync().get().getCode());
        }
        srv.stop();
    }

    @Test
    public void stopNonRunningServer() {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).build();
        assertThrows(IllegalStateException.class, () ->
                srv.stop()
        );

    }

    @Test
    public void startRunningServer() throws IOException {
        CoapServer srv = CoapServerBuilder.newBuilder().transport(0).build();
        srv.start();
        assertThrows(IllegalStateException.class, () ->
                srv.start()
        );

    }

    @Test
    public void testRequestWithPacketDelay() throws Exception {
        CoapServer serverNode = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create(5683)).build();
        serverNode.addRequestHandler("/test/1", new ReadOnlyCoapResource("Dziala"));
        serverNode.start();

        ExecutorService executorService = Executors.newCachedThreadPool();
        CoapServer cnn = CoapServerBuilder.newBuilder()
                .transport(new InMemoryCoapTransport(0, command -> executorService.execute(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException();
                    }
                    command.run();
                }))).build();

        cnn.start();

        CoapPacket request = new CoapPacket(InMemoryCoapTransport.createAddress(5683));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(request);
        assertEquals("Dziala", callback.get().getPayloadString());
        cnn.stop();
    }

    @Test
    public void testRequestWithPacketDropping() throws IOException, CoapException {
        CoapServer srv = CoapServerBuilder.newBuilder()
                .transport(new DroppingPacketsTransportWrapper(CoapConstants.DEFAULT_PORT, (byte) 100))
                .build();
        srv.addRequestHandler("/test", new ReadOnlyCoapResource("TEST"));
        srv.start();

        CoapClient cnn = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(CoapConstants.DEFAULT_PORT))
                .transport(InMemoryCoapTransport.create()).timeout(new SingleTimeout(100)).build();

        assertThrows(CoapTimeoutException.class, () ->
                cnn.resource("/test").sync().get()
        );
    }

    @Test
    public void testMakeRequestWithNullAddress() throws CoapException {
        assertThrows(NullPointerException.class, () ->
            server.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", null))
        );
    }

    @Test
    public void testMakeRequestNullRequest() throws CoapException {
        assertThrows(NullPointerException.class, () ->
            server.makeRequest(new CoapPacket(Method.GET, MessageType.Confirmable, "", null))
        );
    }


    @Test
    public void should_invoke_callback_exceptionally_when_server_stops() throws Exception {
        server.addRequestHandler("/slow", new ReadOnlyCoapResource(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                //ignore
            }
            return "";
        }));

        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(newCoapPacket(serverAddress).mid(11).con().get().uriPath("/slow").build());
        cnn.stop();

        assertThatThrownBy(callback::get).hasCauseInstanceOf(IOException.class);
    }

    @Test
    public void should_invoke_callback_exceptionally_when_server_stops_and_non_request() throws Exception {
        server.addRequestHandler("/slow", new ReadOnlyCoapResource(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                //ignore
            }
            return "";
        }));

        CoapServer cnn = CoapServer.builder().transport(0).build();
        cnn.start();

        CompletableFuture<CoapPacket> callback = cnn.makeRequest(newCoapPacket(serverAddress).mid(11).non().get().token(12).uriPath("/slow").build());
        cnn.stop();

        assertThatThrownBy(callback::get).hasCauseInstanceOf(IOException.class);
    }


    private static class DroppingPacketsTransportWrapper extends InMemoryCoapTransport {
        private final byte probability; //0-100
        private final Random r = new Random();

        private boolean drop() {
            return probability > 0 && r.nextInt(100) < probability;
        }

        private DroppingPacketsTransportWrapper(int port, int probability) {
            super(port);
            if (probability < 0 || probability > 100) {
                throw new IllegalArgumentException("Value must be in range 0-100");
            }
            this.probability = ((byte) probability);
        }


        @Override
        public void receive(InMemoryCoapTransport.DatagramMessage msg) {
            if (!drop()) {
                super.receive(msg);
            }
        }
    }

}
