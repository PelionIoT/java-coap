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
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.internal.DelayedTransactionId;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.CoapResource;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

        server = CoapServer.builder().transport(InMemoryCoapTransport.create())
                .timeout(new SingleTimeout(1000))
                .build();
        server.addRequestHandler("/temp", new ReadOnlyCoapResource("23 C"));

        server.addRequestHandler("/seperate", new CoapResourceSeparateRespImpl("test-content"));
        server.start();
        serverAddr = InMemoryCoapTransport.createAddress(server.getLocalSocketAddress().getPort());
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testLateResponse() throws IOException, CoapException, InterruptedException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryCoapTransport.create()).build();

        Thread.sleep(10);
        assertEquals("test-content", client.resource("/seperate").token(nextToken()).sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void testNonRequest() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryCoapTransport.create()).build();

        assertEquals("test-content", client.resource("/seperate").token(nextToken()).non().sync().get().getPayloadString());

        client.close();
    }

    @Test
    public void testNonRequestWithoutToken() throws IOException, CoapException, InterruptedException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryCoapTransport.create()).build();

        assertEquals("test-content", client.resource("/seperate").non().sync().get().getPayloadString());
        Thread.sleep(40);

        client.close();
    }

    @Test
    public void testNonRequestWithTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryCoapTransport.create())
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
        CoapServer client = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).timeout(new SingleTimeout(100)).build();
        client.start();
        CoapPacket request = new CoapPacket(Code.C205_CONTENT, MessageType.Confirmable, serverAddr);
        request.setToken(nextToken());

        assertEquals(MessageType.Reset, client.makeRequest(request).join().getMessageType());

        client.stop();
    }

    @Test
    public void testUnexpectedNonRequest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).delayedTimeout(100).build();
        cnn.start();
        CoapPacket request = new CoapPacket(Code.C205_CONTENT, MessageType.NonConfirmable, serverAddr);
        request.setToken(nextToken());

        assertEquals(MessageType.Reset, cnn.makeRequest(request).join().getMessageType());

        cnn.stop();
    }

    @Test
    public void shouldReceveNonResponse_withDifferenMID() throws Exception {
        CoapClient client = CoapClientBuilder.newBuilder(serverAddr).transport(InMemoryCoapTransport.create()).build();

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
        CoapServer client = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).build().start();

        CoapPacket badReq = new CoapPacket(Code.C404_NOT_FOUND, MessageType.NonConfirmable, serverAddr);
        badReq.setToken("1".getBytes());

        CoapPacket resp1 = client.makeRequest(badReq).get();
        System.out.println(resp1);
        assertEquals(MessageType.Reset, resp1.getMessageType());

        CoapPacket resp2 = client.makeRequest(badReq).get();
        System.out.println(resp2);
        assertEquals(MessageType.Reset, resp2.getMessageType());

        assertNotEquals(resp1.getMessageId(), resp2.getMessageId());

        client.stop();
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
