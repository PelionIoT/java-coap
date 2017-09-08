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
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author szymon
 */
public class ClientServerWithBlocksTest {

    //static String bigResource = "1111111111111112222222222222333333333333333444444444444445555555555555555555556666666666666666666667777777777777777777788888888888888888888899999999999999999999999000000000000000000000000000111111111111111111111111112222222222222222222223333333333333333333333344444444444444444444444455555555555555555555555566666666666666666666666777777777777777777777777777788888888888888888888999999999999999999999";
    static String BIG_RESOURCE = "The use of web services on the Internet has become ubiquitous in most applications, and depends on the fundamental Representational State Transfer (REST) architecture of the web. The Constrained RESTful Environments (CoRE) working group aims at realizing the REST architecture in a suitable form for the most constrained nodes (e.g. 8-bit microcontrollers with limited RAM and ROM) and networks (e.g. 6LoWPAN).";
    private String dynamicResource = BIG_RESOURCE;
    private ChangeableBigResource changeableBigResource;
    private int SERVER_PORT;

    CoapServer server = null;

    @Before
    public void setUp() throws UnknownHostException, IOException {

        server = CoapServerBuilder.newBuilder().transport(0).blockSize(BlockSize.S_16).setMaxMessageSize(32).build();
        server.addRequestHandler("/bigResource", new StaticBigResource());
        server.addRequestHandler("/dynamic", new DynamicBigResource());
        server.addRequestHandler("/ultra-dynamic", new UltraDynamicBigResource());

        changeableBigResource = new ChangeableBigResource();
        server.addRequestHandler("/chang-res", changeableBigResource);

        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testBlock2Res() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_32).build();

        CoapPacket msg = client.resource("/bigResource").sync().get();
        assertEquals(BIG_RESOURCE.length(), msg.getPayloadString().length());
        assertEquals(BIG_RESOURCE, msg.getPayloadString());
        assertEquals(Code.C205_CONTENT, msg.getCode());

        client.close();

    }

    @Test
    public void testBlock() {
        BlockOption bo = new BlockOption(16, BlockSize.S_16, false);
        BlockOption bo2 = new BlockOption(bo.toBytes());
        assertEquals(bo, bo2);

        bo = new BlockOption(16, BlockSize.S_16, true);
        bo2 = new BlockOption(bo.toBytes());
        assertEquals(bo, bo2);

        bo = new BlockOption(15, BlockSize.S_16, false);
        bo2 = new BlockOption(bo.toBytes());
        assertEquals(bo, bo2);
    }

    @Test
    public void testBlock2Res_2() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_32).build();

        CoapPacket msg = client.resource("/bigResource").sync().get();
        assertEquals(BIG_RESOURCE.length(), msg.getPayloadString().length());
        assertEquals(BIG_RESOURCE, msg.getPayloadString());
        client.close();
    }

    @Test
    public void dynamicBlockResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();

        CoapPacket msg = client.resource("/dynamic").sync().get();
        assertEquals(dynamicResource.length(), msg.getPayloadString().length());
        assertEquals(dynamicResource, msg.getPayloadString());

        client.close();
    }

    @Test
    public void constantlyDynamicBlockResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();
        try {
            CoapPacket msg = client.resource("/ultra-dynamic").sync().get();
            assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, msg.getCode());
        } catch (CoapCodeException ex) {
            assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, ex.getCode());
        } finally {
            client.close();
        }

    }

    @Test
    public void blockRequest() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();

        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void blockRequestWithMoreHeaders() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_256).build();
        changeableBigResource.body = BIG_RESOURCE;
        CoapPacket resp = client.resource("/chang-res").host("test-host").sync().get();

        assertNotNull(changeableBigResource.lastRequest.headers().getUriHost());
        assertEquals("test-host", changeableBigResource.lastRequest.headers().getUriHost());
    }

    @Test
    public void blockRequest256_to_128_switch() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_256).build();

        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(resp.getPayloadString(), Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }


    @Test
    public void blockRequest128() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();

        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void sizeTest() throws Exception {
        final String BODY = "The use of web services on the Internet has become ubiquitous.";
        server.addRequestHandler("/small", new ReadOnlyCoapResource(BODY));

        CoapServer cnn = CoapServerBuilder.newBuilder().blockSize(BlockSize.S_256).transport(0).build().start();
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, "/small", new InetSocketAddress("localhost", SERVER_PORT));
        request.headers().setBlock2Res(new BlockOption(0, BlockSize.S_256, true));
        request.headers().setSize2Res(0);

        CompletableFuture<CoapPacket> resp = cnn.makeRequest(request);

        assertEquals(Code.C205_CONTENT, resp.join().getCode());
        assertEquals(BODY.length(), resp.join().getPayloadString().length());
        assertEquals(BODY, resp.join().getPayloadString());
        assertEquals((Integer) BODY.length(), resp.join().headers().getSize2Res());
    }

    @Test
    public void blockRequest64() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_64).build();
        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapPacket msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void incompleteBlockRequest() throws Exception {
        String body = "gdfgdjgdfgdj";
        // no-block transfers client, we need "pure" server and make block packets in tests
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(0).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_128, true));
        CoapPacket resp = cnn.makeRequest(request).join();

        assertEquals(resp.getPayloadString(), Code.C408_REQUEST_ENTITY_INCOMPLETE, resp.getCode());
        assertTrue(resp.getPayloadString().startsWith("no prev blocks"));
        assertFalse(changeableBigResource.body.equals(body));
    }

    @Test
    public void blockRequestWrongIntermediateBlockSize() throws Exception {
        String body = "0123456789ABC"; //12 bytes
        // no-block transfers client, we need "pure" server and make block packets in tests
        CoapServer client = CoapServerBuilder.newBuilder().transport(0).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));

        request.setPayload(body); //12 bytes, intermediate block
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, true)); // intermediate block, more expected
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));
        CoapPacket resp = makeRequest(client, request);

        assertEquals(resp.getPayloadString(), Code.C400_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getPayloadString().startsWith("mid block"));

        request.setPayload(body + "DEF|"); // 17 bytes
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, true)); // intermediate block, more expected
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));
        resp = makeRequest(client, request);

        assertEquals(resp.getPayloadString(), Code.C400_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getPayloadString().startsWith("mid block"));

        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, false)); // first and last block
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));
        resp = makeRequest(client, request);

        assertEquals(Code.C204_CHANGED, resp.getCode());

    }

    @Test
    public void blockRequestWrongLastBlockSize() throws Exception {
        String body = "0123456789ABCDEF"; //16 bytes
        // no-block transfers client, we need "pure" server and make block packets in tests
        CoapServer client = CoapServerBuilder.newBuilder().transport(0).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body + "0"); // 17 bytes > S_16
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, false)); // first and last block, payload size > block size
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));

        CoapPacket resp = makeRequest(client, request);

        assertEquals(changeableBigResource.body, 0, changeableBigResource.body.length());
        assertEquals(Code.C400_BAD_REQUEST, resp.getCode());

        // check normal case scenario

        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, false)); // first and last block
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));
        resp = makeRequest(client, request);

        assertEquals(Code.C204_CHANGED, resp.getCode());
    }

    @Test
    public void blockRequestWithWrongToken() throws Exception {
        String body = "0123456789abcdef";
        CoapServer client = CoapServerBuilder.newBuilder().transport(0).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, true));
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));
        CoapPacket resp = makeRequest(client, request);

        assertEquals(Code.C231_CONTINUE, resp.getCode());

        request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_16, false));
        request.setToken(DataConvertingUtility.convertVariableUInt(1235L));
        resp = makeRequest(client, request);

        assertFalse("Error code expected", Code.C204_CHANGED == resp.getCode());
        assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, resp.getCode());
    }

    @Test
    public void blockRequestWithWrongNullToken() throws Exception {
        String body = "0123456789ABCDEF";
        CoapServer client = CoapServerBuilder.newBuilder().transport(0).build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_16, true));
        request.setToken(DataConvertingUtility.convertVariableUInt(1234L));

        CoapPacket resp = makeRequest(client, request);
        assertEquals(resp.getPayloadString(), Code.C231_CONTINUE, resp.getCode());

        request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_16, true));
        request.setToken(null);

        resp = makeRequest(client, request);

        assertFalse("Error code expected", Code.C204_CHANGED == resp.getCode());
        assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, resp.getCode());
        assertEquals("Token mismatch", resp.getPayloadString());

    }

    private static CoapPacket makeRequest(CoapServer client, CoapPacket request) throws Exception {
        return client.makeRequest(request).join();
    }

    @Test
    public void blockRequestWithEmptyUrlHeader() throws IOException, CoapException {
        server.addRequestHandler("/", new ReadOnlyCoapResource(BIG_RESOURCE));

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_32).build();

        assertEquals(BIG_RESOURCE, client.resource("").sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void doubleBlockRequestHardcore() throws IOException, CoapException {
        String body = BIG_RESOURCE + "_doubleBlockRequestHardcore";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();
        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().post();

        System.out.println(resp.getPayloadString());

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), resp.getPayload().length);
        assertEquals(body, resp.getPayloadString());
        client.close();
    }

    private class DynamicBigResource extends CoapResource {

        private boolean changed = false;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            exchange.setResponseBody(dynamicResource);
            exchange.getResponseHeaders().setEtag(DataConvertingUtility.intToByteArray(dynamicResource.hashCode()));
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.sendResponse();

            if (!changed) {
                dynamicResource += "-CH";
                changed = true;
            }
        }

    }

    private static class UltraDynamicBigResource extends CoapResource {

        private String dynRes = BIG_RESOURCE;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            dynRes += " C";
            exchange.setResponseBody(dynRes);
            exchange.getResponseHeaders().setEtag(DataConvertingUtility.intToByteArray(dynRes.hashCode()));
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.sendResponse();
        }
    }

    private static class StaticBigResource extends CoapResource {

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            exchange.setResponseBody(BIG_RESOURCE);
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.sendResponse();
        }
    }

    private static class ChangeableBigResource extends CoapResource {

        String body = "";
        CoapPacket lastRequest = null;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            if (exchange.getRequestBody() != null && exchange.getRequestBody().length > 0) {
                //body = exchange.getRequestBody()
                exchange.setResponseBody(exchange.getRequestBody());
            } else {
                exchange.setResponseBody(body);
            }
            exchange.setResponseCode(Code.C205_CONTENT);
            lastRequest = exchange.getRequest();
            exchange.sendResponse();
        }

        @Override
        public void put(CoapExchange exchange) throws CoapCodeException {
            body = exchange.getRequestBodyString();
            exchange.setResponseCode(Code.C204_CHANGED);
            exchange.sendResponse();
        }

        @Override
        public void post(CoapExchange exchange) throws CoapCodeException {
            if (exchange.getRequestHeaders().getBlock2Res() == null) {
                body = exchange.getRequestBodyString();
            }
            exchange.setResponseCode(Code.C204_CHANGED);
            exchange.setResponseBody(body);
            lastRequest = exchange.getRequest();
            exchange.sendResponse();
        }

    }

}
