/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.testng.Assert.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.DataConvertingUtility;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.server.internal.DelayedTransactionId;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.InMemoryTransport;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.SimpleCoapResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class ClientServerNONTest {

    private CoapServer server = null;
    private InetSocketAddress serverAddr = null;

    @BeforeMethod
    public void setUp() throws IOException {
        DelayedTransactionId dti1 = new DelayedTransactionId(new byte[]{13, 14}, new InetSocketAddress(5683));
        DelayedTransactionId dti2 = new DelayedTransactionId(new byte[]{13, 14}, new InetSocketAddress(5683));
        dti1.equals(dti2);

        assertEquals(dti1.hashCode(), dti2.hashCode());
        assertEquals(dti1, dti2);

        server = CoapServer.builder().transport(InMemoryTransport.create())
                .timeout(new SingleTimeout(1000))
                .build();
        server.addRequestHandler("/temp", new SimpleCoapResource("23 C"));

        server.addRequestHandler("/seperate", new CoapResourceSeparateRespImpl("test-content"));
        server.start();
        serverAddr = InMemoryTransport.createAddress(server.getLocalSocketAddress().getPort());
    }

    @AfterMethod
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
        request.setToken(nextToken());

        assertEquals(MessageType.Reset, client.makeRequest(request).join().getMessageType());

        client.close();
    }

    @Test
    public void testUnexpectedNonRequest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create()).delayedTimeout(100).build();
        cnn.start();
        CoapPacket request = new CoapPacket(Code.C205_CONTENT, MessageType.NonConfirmable, serverAddr);
        request.setToken(nextToken());

        assertEquals(MessageType.Reset, cnn.makeRequest(request).join().getMessageType());

        cnn.stop();
    }

    @Test
    public void shouldReceveNonResponse_withDifferenMID() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryTransport.create()).build();

        //------ success
        CoapPacket resp1 = client.resource("/seperate").token(nextToken()).non().sync().get();
        System.out.println(resp1);
        assertEquals("test-content", resp1.getPayloadString());
        CoapPacket resp2 = client.resource("/seperate").token(nextToken()).non().sync().get();
        System.out.println(resp2);
        assertEquals("test-content", resp2.getPayloadString());
        assertNotEquals(resp1.getMessageId(), resp2.getMessageId());

        //------ error
        resp1 = client.resource("/non-existing").token(nextToken()).non().sync().get();
        System.out.println(resp1);
        resp2 = client.resource("/non-existing").token(nextToken()).non().sync().get();
        System.out.println(resp2);
        assertNotEquals(resp1.getMessageId(), resp2.getMessageId());

        client.close();
    }

    @Test
    public void shouldReceveResetResponse_withDifferenMID() throws Exception {
        CoapServer client = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create()).build().start();

        CoapPacket badReq = new CoapPacket(Code.C404_NOT_FOUND, MessageType.NonConfirmable, serverAddr);
        badReq.setToken("1".getBytes());

        CoapPacket resp1 = client.makeRequest(badReq).get();
        System.out.println(resp1);
        assertEquals(MessageType.Reset, resp1.getMessageType());

        CoapPacket resp2 = client.makeRequest(badReq).get();
        System.out.println(resp2);
        assertEquals(MessageType.Reset, resp2.getMessageType());

        assertNotEquals(resp1.getMessageId(), resp2.getMessageId());

        client.close();
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
            if (ex.getRequest().getMustAcknowledge()) {
                ex.sendDelayedAck();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex1) {
                    ex1.printStackTrace();
                }
            }

            CoapPacket resp = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, ex.getRemoteAddress());
            if (!ex.getRequest().getMustAcknowledge()) {
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
     * @return random token
     */
    private static byte[] nextToken() {
        return DataConvertingUtility.convertVariableUInt((new Random().nextInt(0xFFFFF)));
    }
}
