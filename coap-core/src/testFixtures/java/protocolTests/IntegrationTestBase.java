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

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.Opaque.of;
import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.CoapConstants;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.linkformat.LinkFormat;
import com.mbed.coap.linkformat.LinkFormatBuilder;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.ObservableResourceService;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.utils.Bytes;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.ObservationListener;


abstract class IntegrationTestBase {

    CoapServer server = null;
    CoapClient client;

    private ObservableResourceService obsResource;
    private final Opaque largePayload = Bytes.opaqueOfRandom(3000);
    private final ObservationListener listener = new ObservationListener();
    private CompletableFuture<CoapResponse> slowResourcePromise;

    @BeforeEach
    public void setUp() throws IOException {
        obsResource = new ObservableResourceService(CoapResponse.ok(""));
        slowResourcePromise = new CompletableFuture<>();
        TestResource testResource = new TestResource();
        final Service<CoapRequest, CoapResponse> route = RouterService.builder()
                .get("/", __ -> completedFuture(CoapResponse.ok("Shortest path")))
                .get("/test/1", req ->
                        completedFuture(CoapResponse.ok("Dziala", MediaTypes.CT_TEXT_PLAIN))
                )
                .get("/test2", testResource)
                .put("/test2", testResource)
                .post("/test2", testResource)
                .delete("/test2", testResource)
                .get("/large", __ -> completedFuture(
                        CoapResponse.ok(largePayload)
                ))
                .post("/large", req -> completedFuture(
                        CoapResponse.ok("Got " + req.getPayload().size() + "B")
                ))
                .get("/obs", obsResource)
                .get("/slow", __ -> slowResourcePromise)
                .get(CoapConstants.WELL_KNOWN_CORE, req ->
                        completedFuture(CoapResponse.ok("<test/1>,<test2>", MediaTypes.CT_APPLICATION_LINK__FORMAT))
                ).build();

        server = buildServer(0, route).start();

        int port = server.getLocalSocketAddress().getPort();
        client = buildClient(port);
    }

    abstract protected CoapClient buildClient(int port) throws IOException;

    abstract protected CoapServer buildServer(int port, Service<CoapRequest, CoapResponse> route) throws IOException;

    @AfterEach
    public void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.stop();
        assertTrue(listener.noMoreReceived());
    }

    @Test
    public void resourceManipulationTest() throws CoapException, IOException {
        //Getting resources
        assertEquals("Dziala", client.sendSync(get("/test/1")).getPayloadString());

        //posting to a resource with error
        assertEquals(Code.C400_BAD_REQUEST, client.sendSync(post("/test2")).getCode());

        //getting non-existing resource
        assertEquals(Code.C404_NOT_FOUND, client.sendSync(get("/do-not-exist")).getCode());

        CoapResponse msg = client.sendSync(get("/.well-known/core"));
        assertNotNull(msg);
    }

    @Test
    public void requestWithAccept() throws Exception {
        CoapRequest request = get("/test2").accept(MediaTypes.CT_APPLICATION_JSON);

        assertEquals(Code.C406_NOT_ACCEPTABLE, client.sendSync(request).getCode());
    }

    @Test
    public void simpleRequestWithCustomHeader() throws Exception {
        CoapRequest request = get("/test/1")
                .options(o -> o.put(74, Opaque.variableUInt(0x010203L)));

        assertEquals("Dziala", client.sendSync(request).getPayloadString());
    }

    @Test
    public void simpleRequestWithCriticalCustomHeader() throws Exception {
        CoapRequest request = get("/test/1")
                .options(o -> o.put(71, Opaque.variableUInt(0x010203L)));

        assertEquals(Code.C402_BAD_OPTION, client.sendSync(request).getCode());
    }

    @Test
    public void simpleRequestWithNonCriticalCustomHeader() throws Exception {
        CoapRequest request = get("/test/1")
                .options(o -> o.put(72, Opaque.ofBytes(1, 2, 3)));

        assertEquals("Dziala", client.sendSync(request).getPayloadString());
    }

    @Test
    public void simpleRequestToShortestPath() throws Exception {
        assertEquals(
                "Shortest path",
                client.sendSync(get("/")).getPayloadString()
        );
    }

    @Test
    public void slowResource() {
        // given
        CompletableFuture<CoapResponse> resp = client.send(get("/slow").token(0x0102030405060708L));
        assertFalse(resp.isDone());

        // when
        slowResourcePromise.complete(CoapResponse.ok("OK"));

        // then
        assertEquals(CoapResponse.ok("OK"), resp.join());
    }

    @Test
    public void wellKnownResourcesTest() throws IOException, CoapException, ParseException {
        CoapResponse msg = client.sendSync(get(CoapConstants.WELL_KNOWN_CORE));

        assertNotNull(msg);
        LinkFormat[] links = LinkFormatBuilder.parseList(msg.getPayloadString());
        assertEquals(2, links.length);
    }

    @Test
    public void sendPing() throws Exception {
        CoapResponse pingResp = client.ping().get();

        assertEquals(CoapResponse.of(null), pingResp);
    }

    @Test
    void readLargePayload() {
        CoapResponse resp = client.send(get("/large").token(101)).join();

        assertEquals(CoapResponse.ok(largePayload), resp.options(it -> it.setBlock2Res(null)));
    }

    @Test
    void writeLargePayload() {
        await().untilAsserted(() -> {
            CoapResponse resp = client.send(post("/large").token(102).payload(largePayload)).join();

            assertEquals(CoapResponse.ok("Got 3000B"), resp.options(it -> it.setBlock1Req(null)));
        });
    }

    @Test
    void observeResource() throws InterruptedException {
        // given
        CoapResponse observe = client.observe("/obs", listener).join();
        assertEquals(CoapResponse.ok("").observe(0), observe);

        // when
        assertTrue(obsResource.putPayload(Opaque.of("obs1")));
        await().until(() -> obsResource.putPayload(Opaque.of("obs2")));

        // then
        listener.verifyReceived(CoapResponse.ok("obs1").observe(1));
        listener.verifyReceived(CoapResponse.ok("obs2").observe(2));
    }

    @Test
    void observeLargeResource() throws InterruptedException {
        // given
        CoapResponse observe = client.observe("/obs", listener).join();
        assertEquals(CoapResponse.ok("").observe(0), observe);

        // when
        assertTrue(obsResource.putPayload(largePayload));

        // then
        assertEquals(CoapResponse.ok(largePayload), listener.take().options(it -> it.setBlock2Res(null)));
    }

    @Test
    public void should_invoke_callback_exceptionally_when_server_stops() throws Exception {
        CompletableFuture<CoapResponse> resp = client.send(get("/slow"));
        client.close();
        client = null;

        assertThatThrownBy(() -> resp.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
    }

    private static class TestResource implements Service<CoapRequest, CoapResponse> {

        private Opaque payload = of("Dziala2");
        private short contentType = MediaTypes.CT_TEXT_PLAIN;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest request) {
            switch (request.getMethod()) {
                case GET:
                    return completedFuture(get(request));
                case POST:
                    return failedFuture(new CoapCodeException(Code.C400_BAD_REQUEST));
                case PUT:
                    return completedFuture(put(request));
                case DELETE:
                    return completedFuture(delete());
                default:
                    return failedFuture(new RuntimeException());
            }
        }

        public CoapResponse get(CoapRequest request) {
            if (request.options().getAccept() != null) {
                boolean isFound = false;
                if (request.options().getAccept() == contentType) {
                    isFound = true;
                }

                if (!isFound) {
                    //did not found accepted content type
                    return CoapResponse.of(Code.C406_NOT_ACCEPTABLE);
                }
            }

            return CoapResponse.ok(payload, contentType);
        }

        public CoapResponse put(CoapRequest request) {
            payload = request.getPayload();
            contentType = request.options().getContentFormat();
            return CoapResponse.of(Code.C204_CHANGED);
        }

        public CoapResponse delete() {
            payload = EMPTY;
            contentType = 0;
            return CoapResponse.of(Code.C202_DELETED);
        }

    }
}
