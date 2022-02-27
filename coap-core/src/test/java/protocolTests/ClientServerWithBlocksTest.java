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

import static com.mbed.coap.packet.Opaque.*;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class ClientServerWithBlocksTest {

    //static String bigResource = "1111111111111112222222222222333333333333333444444444444445555555555555555555556666666666666666666667777777777777777777788888888888888888888899999999999999999999999000000000000000000000000000111111111111111111111111112222222222222222222223333333333333333333333344444444444444444444444455555555555555555555555566666666666666666666666777777777777777777777777777788888888888888888888999999999999999999999";
    static Opaque BIG_RESOURCE = of("The use of web services on the Internet has become ubiquitous in most applications, and depends on the fundamental Representational State Transfer (REST) architecture of the web. The Constrained RESTful Environments (CoRE) working group aims at realizing the REST architecture in a suitable form for the most constrained nodes (e.g. 8-bit microcontrollers with limited RAM and ROM) and networks (e.g. 6LoWPAN).");
    private Opaque dynamicResource = BIG_RESOURCE;
    private ChangeableBigResource changeableBigResource;
    private int SERVER_PORT = 5683;
    private final Opaque BODY = of("The use of web services on the Internet has become ubiquitous.");

    CoapServer server = null;

    @BeforeEach
    public void setUp() throws IOException {

        changeableBigResource = new ChangeableBigResource();
        server = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create(5683)).blockSize(BlockSize.S_32).maxMessageSize(64)
                .route(RouterService.builder()
                        .get("/bigResource", __ -> completedFuture(CoapResponse.ok(BIG_RESOURCE)))
                        .get("/", __ -> completedFuture(CoapResponse.ok(BIG_RESOURCE)))
                        .get("/small", __ -> completedFuture(CoapResponse.ok(BODY)))
                        .get("/dynamic", new DynamicBigResource())
                        .get("/ultra-dynamic", new UltraDynamicBigResource())
                        .get("/chang-res", changeableBigResource)
                        .post("/chang-res", changeableBigResource)
                        .put("/chang-res", changeableBigResource)
                )
                .build();

        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testBlock2Res() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_32).build();

        CoapPacket msg = client.resource("/bigResource").sync().get();
        assertEquals(BIG_RESOURCE, msg.getPayload());
        assertEquals(Code.C205_CONTENT, msg.getCode());

        client.close();

    }

    @Test
    public void testBlock2Res_2() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_32).build();

        CoapPacket msg = client.resource("/bigResource").sync().get();
        assertEquals(BIG_RESOURCE, msg.getPayload());
        client.close();
    }

    @Test
    public void dynamicBlockResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_128).build();

        CoapPacket msg = client.resource("/dynamic").sync().get();
        assertEquals(dynamicResource, msg.getPayload());

        client.close();
    }

    @Test
    public void constantlyDynamicBlockResource() throws IOException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_128).build();

        assertThatThrownBy(() -> client.resource("/ultra-dynamic").sync().get()).isExactlyInstanceOf(CoapBlockException.class);
        client.close();
    }

    @Test
    public void blockRequest() throws IOException, CoapException {
        Opaque body = BIG_RESOURCE.concat(of("d"));

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_128).build();

        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayload());
    }

    @Test
    public void blockRequestWithMoreHeaders() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_256).build();
        changeableBigResource.body = BIG_RESOURCE;
        CoapPacket resp = client.resource("/chang-res").host("test-host").sync().get();

        assertNotNull(changeableBigResource.lastRequest.options().getUriHost());
        assertEquals("test-host", changeableBigResource.lastRequest.options().getUriHost());
    }

    @Test
    public void blockRequest256_to_32_switch() throws IOException, CoapException {
        Opaque body = BIG_RESOURCE.concat(of("d"));

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_256).build();

        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode(), resp.getPayloadString());
        assertEquals(32, resp.headers().getBlock1Req().getSize());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayload());
    }

    @Test
    public void blockRequest_size_negotiation() throws IOException, CoapException {
        CoapTransport limitedTransport = new InMemoryCoapTransport() {
            @Override
            public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
                //emulate network that cuts data it larger that 40 bytes
                if (coapPacket.getPayload().size() > 40) {
                    coapPacket.setPayload(coapPacket.getPayload().slice(0, 40));
                }
                super.sendPacket0(coapPacket, adr, tranContext);
            }
        };

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(limitedTransport).blockSize(BlockSize.S_64).build();

        CoapPacket resp = client.resource("/chang-res").payload(BIG_RESOURCE).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode(), resp.getPayloadString());
        assertEquals(32, resp.headers().getBlock1Req().getSize());
    }

    @Test
    public void sizeTest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().blockSize(BlockSize.S_256).transport(InMemoryCoapTransport.create()).build().start();
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, "/small", new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));
        request.headers().setBlock2Res(new BlockOption(0, BlockSize.S_256, true));
        request.headers().setSize2Res(0);

        CompletableFuture<CoapPacket> resp = cnn.makeRequest(request);

        assertEquals(Code.C205_CONTENT, resp.join().getCode());
        assertEquals(BODY, resp.join().getPayload());
        assertEquals((Integer) BODY.size(), resp.join().headers().getSize2Res());
    }

    @Test
    public void blockRequest64() throws IOException, CoapException {
        Opaque body = BIG_RESOURCE.concat(of("d"));

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_64).build();
        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayload());
    }

    @Test
    public void incompleteBlockRequest() throws Exception {
        String body = "gdfgdjgdfgdj";
        // no-block transfers client, we need "pure" server and make block packets in tests
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress(InetAddress.getLocalHost(), SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_128, true));
        CoapPacket resp = cnn.makeRequest(request).join();

        assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, resp.getCode(), resp.getPayloadString());
        assertTrue(resp.getPayloadString().startsWith("no prev blocks"));
        assertFalse(changeableBigResource.body.equals(body));
    }


    @Test
    public void blockRequestWithEmptyUrlHeader() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_32).build();

        assertEquals(BIG_RESOURCE, client.resource("").sync().get().getPayload());

        client.close();
    }

    @Test
    public void doubleBlockRequestHardcore() throws IOException, CoapException {
        String body = BIG_RESOURCE + "_doubleBlockRequestHardcore";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).transport(InMemoryCoapTransport.create()).blockSize(BlockSize.S_128).build();
        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().post();

        System.out.println(resp.getPayloadString());

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), resp.getPayload().size());
        assertEquals(body, resp.getPayloadString());
        client.close();
    }

    private class DynamicBigResource implements Service<CoapRequest, CoapResponse> {

        private boolean changed = false;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            final CoapResponse resp = new CoapResponse(Code.C205_CONTENT, dynamicResource, opts ->
                    opts.setEtag(Opaque.variableUInt(dynamicResource.hashCode()))
            );

            if (!changed) {
                dynamicResource = dynamicResource.concat(of("-CH"));
                changed = true;
            }
            return completedFuture(resp);
        }

    }

    private static class UltraDynamicBigResource implements Service<CoapRequest, CoapResponse> {

        private Opaque dynRes = BIG_RESOURCE;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            dynRes = dynRes.concat(of(" C"));
            final CoapResponse resp = new CoapResponse(Code.C205_CONTENT, dynRes, opts ->
                    opts.setEtag(Opaque.variableUInt(dynRes.hashCode()))
            );
            return completedFuture(resp);
        }
    }

    private static class ChangeableBigResource implements Service<CoapRequest, CoapResponse> {

        Opaque body = EMPTY;
        CoapRequest lastRequest = null;

        @Override
        public CompletableFuture<CoapResponse> apply(CoapRequest req) {
            lastRequest = req;
            switch (req.getMethod()) {
                case GET:
                    if (req.getPayload().size() > 0) {
                        //body = exchange.getRequestBody()
                        return completedFuture(CoapResponse.ok(req.getPayload()));
                    } else {
                        return completedFuture(CoapResponse.ok(body));
                    }
                case PUT:
                    body = req.getPayload();
                    return completedFuture(CoapResponse.of(Code.C204_CHANGED));
                case POST:
                    if (req.options().getBlock2Res() == null) {
                        body = req.getPayload();
                    }
                    return completedFuture(CoapResponse.of(Code.C204_CHANGED, body));
            }
            throw new IllegalStateException();
        }

    }

}
