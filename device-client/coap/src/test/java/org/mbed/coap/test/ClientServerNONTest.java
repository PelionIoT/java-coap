package org.mbed.coap.test;

import org.mbed.coap.server.CoapServerBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.internal.DelayedTransactionId;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.SimpleCoapResource;
import org.mbed.coap.utils.SyncCallback;

/**
 * @author szymon
 */
public class ClientServerNONTest {

    private CoapServer server = null;
    private InetSocketAddress serverAddr = null;

    @Before
    public void setUp() throws IOException {
        DelayedTransactionId dti1 = new DelayedTransactionId(new byte[]{13, 14}, new InetSocketAddress(5683));
        DelayedTransactionId dti2 = new DelayedTransactionId(new byte[]{13, 14}, new InetSocketAddress(5683));
        dti1.equals(dti2);

        assertEquals(dti1.hashCode(), dti2.hashCode());
        assertEquals(dti1, dti2);

        server = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create())
                .timeout(new SingleTimeout(1000))
                .build();
        server.addRequestHandler("/temp", new SimpleCoapResource("23 C"));

        server.addRequestHandler("/seperate", new CoapResourceSeparateRespImpl("test-content"));
        server.start();
        serverAddr = InMemoryTransport.createAddress(server.getLocalSocketAddress().getPort());
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testLateResponse() throws IOException, CoapException, InterruptedException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryTransport.create()).build();

        Thread.sleep(10);
        assertEquals("test-content", client.resource("/seperate").token(nextToken()).sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void testNonRequest() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryTransport.create()).build();

        assertEquals("test-content", client.resource("/seperate").token(nextToken()).non().sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void testNonRequestWithoutToken() throws IOException, CoapException, InterruptedException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryTransport.create()).build();

        assertEquals("test-content", client.resource("/seperate").non().sync().get().getPayloadString());
        Thread.sleep(40);

        client.close();
    }

    @Test
    public void testNonRequestWithTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryTransport.create())
                .delayedTransTimeout(100).build();

        try {
            client.resource("/seperate").query("timeout", "t").token(nextToken()).non().sync().get();
        } catch (CoapTimeoutException ex) {
            //expected
        }

        client.close();
    }

    @Test
    public void testUnexpectedConRequest() throws Exception {
        CoapServer client = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create()).timeout(new SingleTimeout(100)).build();
        client.start();
        CoapPacket request = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, serverAddr);
        request.setRemoteAddress(serverAddr);
        request.setToken(nextToken());

        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        client.makeRequest(request, callback);
        assertEquals(MessageType.Reset, callback.getResponse().getMessageType());

        client.close();
    }

    @Test
    public void testUnexpectedNonRequest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create()).delayedTimeout(100).build();
        cnn.start();
        CoapPacket request = new CoapPacket(Code.C205_CONTENT, MessageType.NonConfirmable, serverAddr);
        request.setToken(nextToken());

        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        cnn.makeRequest(request, callback);
        assertEquals(MessageType.Reset, callback.getResponse().getMessageType());

        cnn.stop();
    }

    private static class CoapResourceSeparateRespImpl extends CoapResource {

        private final String body;

        public CoapResourceSeparateRespImpl(String body) {
            this.body = body;
        }

        @Override
        public void get(CoapExchange ex) throws CoapCodeException {
            if (ex.getRequestHeaders().getUriQuery() != null && ex.getRequestHeaders().getUriQuery().equals("timeout=t")) {
                return;
            }
            if (ex.getRequest().getMustAcknowladge()) {
                ex.sendDelayedAck();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex1) {
                    ex1.printStackTrace();
                }
            }

            CoapPacket resp = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, ex.getRemoteAddress());
            if (!ex.getRequest().getMustAcknowladge()) {
                resp.setMessageType(MessageType.NonConfirmable);
            }
            resp.setPayload(body);
            resp.setToken(ex.getRequest().getToken());
            ex.setResponse(resp);
            ex.sendResponse();
        }
    }

    /**
     * Returns random token number
     *
     * @return
     */
    private static byte[] nextToken() {
        return HeaderOptions.convertVariableUInt((new Random().nextInt(0xFFFFF)));
    }
}
