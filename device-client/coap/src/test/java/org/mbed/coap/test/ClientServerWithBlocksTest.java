package org.mbed.coap.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.BlockOption;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapMessage;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.test.utils.Utils;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.SimpleCoapResource;
import org.mbed.coap.utils.SyncCallback;

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

        server = CoapServerBuilder.newBuilder().build();
        server.addRequestHandler("/bigResource", new StaticBigResource());
        server.addRequestHandler("/dynamic", new DynamicBigResource());
        server.addRequestHandler("/ultra-dynamic", new UltraDynamicBigResource());

        changeableBigResource = new ChangeableBigResource();
        server.addRequestHandler("/chang-res", changeableBigResource);

        server.setBlockSize(BlockSize.S_128);
        server.start();
        SERVER_PORT = server.getLocalSocketAddress().getPort();
//        server = new CoapServerOLD();
//        server.addHandler("/test/1", new SimpleCoapResource("Dziala"));
//        server.addHandler("/test2", new TestResource());
//        server.addHandler("/bigResource", new BigResource() );
//        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testBlock2Res() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();

        CoapMessage msg = client.resource("/bigResource").sync().get();
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
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();
        server.setBlockSize(BlockSize.S_128);

        CoapMessage msg = client.resource("/bigResource").sync().get();
        assertEquals(BIG_RESOURCE.length(), msg.getPayloadString().length());
        assertEquals(BIG_RESOURCE, msg.getPayloadString());
        client.close();
    }

    @Test
    public void dynamicBlockResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();

        CoapMessage msg = client.resource("/dynamic").sync().get();
        assertEquals(dynamicResource.length(), msg.getPayloadString().length());
        assertEquals(dynamicResource, msg.getPayloadString());

        client.close();
    }

    @Test
    public void constantlyDynamicBlockResource() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();
        try {
            CoapMessage msg = client.resource("/ultra-dynamic").sync().get();
            assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, msg.getCode());
        } catch (CoapCodeException ex) {
            assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, ex.getCode());
        } finally {
            client.close();
        }
        assertTrue("Exception expected", true);

    }

    @Test
    public void blockRequest() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();

        CoapMessage resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapMessage msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void blockRequestWithMoreHeaders() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_256).build();
        changeableBigResource.body = BIG_RESOURCE;
        CoapMessage resp = client.resource("/chang-res").host("test-host").sync().get();

        assertNotNull(changeableBigResource.lastRequest.headers().getUriHost());
        assertEquals("test-host", changeableBigResource.lastRequest.headers().getUriHost());
    }

    @Test
    public void blockRequest256() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_256).build();

        CoapMessage resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapMessage msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void sizeTest() throws Exception {
        final String BODY = "The use of web services on the Internet has become ubiquitous.";
        server.addRequestHandler("/small", new SimpleCoapResource(BODY));

        CoapServer cnn = CoapServerBuilder.newBuilder().blockSize(BlockSize.S_256).build().start();
        CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, "/small", new InetSocketAddress("localhost", SERVER_PORT));
        request.headers().setBlock2Res(new BlockOption(0, BlockSize.S_256, true));
        request.headers().setSize1(0);

        SyncCallback<CoapPacket> syncCallback = new SyncCallback<>();
        cnn.makeRequest(request, syncCallback);

        assertEquals(Code.C205_CONTENT, syncCallback.getResponse().getCode());
        assertEquals(BODY.length(), syncCallback.getResponse().getPayloadString().length());
        assertEquals(BODY, syncCallback.getResponse().getPayloadString());
        assertEquals((Integer) BODY.length(), syncCallback.getResponse().headers().getSize1());
    }

    @Test
    public void blockRequest64() throws IOException, CoapException {
        String body = BIG_RESOURCE + "d";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_64).build();
        CoapMessage resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().put();

        assertEquals(Code.C204_CHANGED, resp.getCode());
        assertEquals(body.length(), changeableBigResource.body.length());
        assertEquals(body, changeableBigResource.body);

        CoapMessage msg = client.resource("/chang-res").sync().get();

        assertEquals(body, msg.getPayloadString());
    }

    @Test
    public void incompleteBlockRequest() throws Exception {
        String body = "gdfgdjgdfgdj";
        CoapServer cnn = CoapServerBuilder.newBuilder().build().start();
//        CoapConnection cnn = CoapConnection.create(new InetSocketAddress("127.0.0.1", SERVER_PORT));

//        CoapPacket request = cnn.makeCoapMessage(Method.PUT, "/chang-res", body.getBytes(), 0);
        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_128, true));
        SyncCallback<CoapPacket> syncCallback = new SyncCallback<>();
        cnn.makeRequest(request, syncCallback);
        CoapPacket resp = syncCallback.getResponse();

        assertEquals(Code.C408_REQUEST_ENTITY_INCOMPLETE, resp.getCode());
        assertFalse(changeableBigResource.body.equals(body));
    }

    @Test
    public void blockRequestWithWrongToken() throws Exception {
        String body = "gdfgdjgdfgdj";
        CoapServer client = CoapServerBuilder.newBuilder().build().start();
//        CoapConnection cnn = CoapConnection.create(new InetSocketAddress("127.0.0.1", SERVER_PORT));

//        CoapPacket request = cnn.makeCoapMessage(Method.PUT, "/chang-res", body.getBytes(), 0);
        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_128, true));
        request.setToken(HeaderOptions.convertVariableUInt(1234L));
        CoapPacket resp = makeRequest(client, request);

        assertEquals(Code.C204_CHANGED, resp.getCode());

        request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_128, true));
        request.setToken(HeaderOptions.convertVariableUInt(1235L));
        resp = makeRequest(client, request);

        assertFalse("Error code expected", Code.C204_CHANGED == resp.getCode());

    }

    @Test
    public void blockRequestWithWrongNullToken() throws Exception {
        String body = "gdfgdjgdfgdj";
        CoapServer client = CoapServerBuilder.newBuilder().build().start();

        CoapPacket request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(0, BlockSize.S_128, true));
        request.setToken(HeaderOptions.convertVariableUInt(1234L));

        CoapPacket resp = makeRequest(client, request);
        assertEquals(Code.C204_CHANGED, resp.getCode());

        request = new CoapPacket(Method.PUT, MessageType.Confirmable, "/chang-res", new InetSocketAddress("127.0.0.1", SERVER_PORT));
        request.setPayload(body);
        request.headers().setBlock1Req(new BlockOption(1, BlockSize.S_128, true));
        request.setToken(null);

        resp = makeRequest(client, request);

        assertFalse("Error code expected", Code.C204_CHANGED == resp.getCode());

    }

    private static CoapPacket makeRequest(CoapServer client, CoapPacket request) throws Exception {
        SyncCallback<CoapPacket> syncCallback = new SyncCallback<>();
        client.makeRequest(request, syncCallback);
        return syncCallback.getResponse();
    }

    @Test
    public void blockRequestWithEmptyUrlHeader() throws IOException, CoapException {
        server.addRequestHandler("/", new SimpleCoapResource(BIG_RESOURCE));

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).build();

        assertEquals(BIG_RESOURCE, client.resource("").sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void doubleBlockRequestHardcore() throws IOException, CoapException {
        String body = BIG_RESOURCE + "_doubleBlockRequestHardcore";

        CoapClient client = CoapClientBuilder.newBuilder(SERVER_PORT).blockSize(BlockSize.S_128).build();
        CoapPacket resp = client.resource("/chang-res").payload(body, MediaTypes.CT_TEXT_PLAIN).sync().post();

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
            exchange.getResponseHeaders().setEtag(Utils.intToByteArray(dynamicResource.hashCode()));
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
            exchange.getResponseHeaders().setEtag(Utils.intToByteArray(dynRes.hashCode()));
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
