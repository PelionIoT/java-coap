/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.transport.InMemoryTransport;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.CoapResource;

/**
 * @author szymon
 */
public class CoapServerTransportContextTest {

    private CoapServer server;
    private final CoapResourceTest coapResourceTest = new CoapResourceTest();
    private final InMemoryTransport srvTransport = spy(new InMemoryTransport(5683));

    @Before
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(srvTransport).build();
        server.addRequestHandler("/test", coapResourceTest);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testRequest() throws IOException, CoapException {
        InMemoryTransport cliTransport = spy(new InMemoryTransport());
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683)).transport(cliTransport).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        client.resource("/test").context(new TextTransportContext("client-sending")).sync().get();
        assertEquals("dupa", ((TextTransportContext) coapResourceTest.transportContext).getText());
        verify(cliTransport).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("client-sending")));
        verify(srvTransport).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("get-response")));

        srvTransport.setTransportContext(new TextTransportContext("dupa2"));
        client.resource("/test").sync().get();
        assertEquals("dupa2", ((TextTransportContext) coapResourceTest.transportContext).getText());

        client.close();
    }

    @Test
    public void testRequestWithBlocks() throws IOException, CoapException {
        InMemoryTransport cliTransport = spy(new InMemoryTransport());
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683)).transport(cliTransport).blockSize(BlockSize.S_16).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        CoapPacket resp = client.resource("/test").payload("fhdkfhsdkj fhsdjkhfkjsdh fjkhs dkjhfsdjkh")
                .context(new TextTransportContext("client-block")).sync().put();

        assertEquals(Code.C201_CREATED, resp.getCode());
        assertEquals("dupa", ((TextTransportContext) coapResourceTest.transportContext).getText());

        //for each block it sends same transport context
        verify(cliTransport, times(3)).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("client-block")));

        client.close();
    }

    private static class TextTransportContext extends TransportContext {

        private final String text;

        public TextTransportContext(String text) {
            super(null, null, null, null);
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TextTransportContext)) {
                return false;
            }

            TextTransportContext that = (TextTransportContext) o;

            if (!text.equals(that.text)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }
    }

    private static class CoapResourceTest extends CoapResource {

        TransportContext transportContext;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            transportContext = exchange.getRequestTransportContext();
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.setResponseTransportContext(new TextTransportContext("get-response"));
            exchange.sendResponse();
        }

        @Override
        public void put(CoapExchange exchange) throws CoapCodeException {
            transportContext = exchange.getRequestTransportContext();
            exchange.setResponseCode(Code.C201_CREATED);
            exchange.setResponseTransportContext(new TextTransportContext("put-response"));
            exchange.send();
        }

    }
}
