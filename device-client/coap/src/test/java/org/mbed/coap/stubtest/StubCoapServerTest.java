/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.stubtest;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.test.StubCoapServer;

/**
 *
 * @author szymon
 */
public class StubCoapServerTest {

    StubCoapServer stub;
    CoapClient client;

    @Before
    public void setUp() throws IOException {
        stub = new StubCoapServer();
        //stub = new StubCoapServer(1000000);
        stub.start();

        client = CoapClientBuilder.newBuilder(stub.getAddress()).build();
    }

    @After
    public void tearDown() {
        stub.stop();
        client.close();
    }

    @Test
    public void testStub() throws CoapException {

        stub.when("/dupa").then().withContentFormat(MediaTypes.CT_TEXT_PLAIN).retrn("blada");

        stub.when("/dupa/2").thenReturn("blada2");
        stub.when("/dupa/3").thenReturn(Code.C401_UNAUTHORIZED);
        stub.when("/dupa/4").withQuery("b=3&d=2").thenReturn(Code.C415_UNSUPPORTED_MEDIA_TYPE);
        stub.whenDELETE("/del").thenReturn();

        assertEquals("blada", client.resource("/dupa").sync().get().getPayloadString());
        assertEquals("blada2", client.resource("/dupa/2").sync().get().getPayloadString());
        assertEquals(Code.C401_UNAUTHORIZED, client.resource("/dupa/3").sync().get().getCode());
        assertEquals(Code.C415_UNSUPPORTED_MEDIA_TYPE, client.resource("/dupa/4").query("d", "2").query("b", "3").sync().get().getCode());
        assertEquals(Code.C404_NOT_FOUND, client.resource("/dupa/4").sync().get().getCode());
        assertNotNull(stub.verify("/dupa/2"));

        stub.whenPOST("/d").withContentFormat(MediaTypes.CT_APPLICATION_JSON).thenReturn();
        stub.whenPOST("/d").withObserve().withContentFormat(MediaTypes.CT_APPLICATION_XML).thenReturn();
        stub.whenPOST("/d").thenReturn(Code.C403_FORBIDDEN);
        assertEquals(Code.C201_CREATED, client.resource("/d").payload("ff", MediaTypes.CT_APPLICATION_JSON).sync().post().getCode());
        assertEquals(Code.C403_FORBIDDEN, client.resource("/d").payload("ff", MediaTypes.CT_APPLICATION_XML).sync().post().getCode());
        assertEquals(Code.C403_FORBIDDEN, client.resource("/d").payload("ff1", MediaTypes.CT_APPLICATION_EXI).sync().post().getCode());

    }

    @Test
    public void testStub2() throws CoapException {

        stub.when("/dupa").then().withContentFormat(MediaTypes.CT_TEXT_PLAIN).retrn("blada");
        stub.whenDELETE("/dupa").withBody("del").thenReturn();

        assertEquals((Short) MediaTypes.CT_TEXT_PLAIN, client.resource("/dupa").sync().get().headers().getContentFormat());
        assertEquals(Code.C405_METHOD_NOT_ALLOWED, client.resource("/dupa").sync().put().getCode());
        assertEquals(Code.C400_BAD_REQUEST, client.resource("/dupa").sync().delete().getCode());
    }

    @Test
    public void payloadRules() throws CoapException {

        stub.whenPUT("/e").withBody("hh").thenReturn("hh");
        stub.whenPUT("/e").withBody().withContentFormat(MediaTypes.CT_TEXT_PLAIN).thenReturn("1");
        stub.whenPUT("/e").thenReturn(Code.C406_NOT_ACCEPTABLE);

        assertEquals(Code.C204_CHANGED, client.resource("/e").payload("ff1", MediaTypes.CT_TEXT_PLAIN).sync().put().getCode());
        assertEquals(Code.C406_NOT_ACCEPTABLE, client.resource("/e").payload(new byte[0], MediaTypes.CT_TEXT_PLAIN).sync().put().getCode());
        assertEquals("hh", client.resource("/e").payload("hh", MediaTypes.CT_TEXT_PLAIN).sync().put().getPayloadString());
    }

    @Test
    public void reset() throws CoapException {
        stub.when("/reset").thenReturn();
        assertEquals(Code.C205_CONTENT, client.resource("/reset").sync().get().getCode());
        assertNotNull(stub.verify("/reset"));

        stub.reset();
        assertNull(stub.verify("/reset"));
        assertEquals(Code.C404_NOT_FOUND, client.resource("/reset").sync().get().getCode());
    }

    @Test
    public void verify_timeout() throws Exception {
        stub.when("/test").thenReturn();
        assertNull(stub.verifyPUT("/test"));
        Future<CoapPacket> fiut = Executors.newSingleThreadExecutor().submit(() -> stub.verifyPUT("/test", 10));
        //Thread.sleep(1000);
        client.resource("/test").maxAge(265758953).sync().put();
        assertNotNull(fiut.get(10, TimeUnit.SECONDS));
        assertEquals(265758953, fiut.get().headers().getMaxAgeValue());

        client.resource("/test").sync().delete();
        assertNotNull(stub.verifyDELETE("/test", 1));
        client.resource("/test").sync().get();
        assertNotNull(stub.verify("/test", 1));
        client.resource("/test").sync().post();
        assertNotNull(stub.verifyPOST("/test", 0));

        assertNull(stub.verifyPOST("/test-not", 1));
    }

    @Test
    public void send() throws IOException, CoapException {
        StubCoapServer stub2 = new StubCoapServer();
        stub2.start();
        stub2.when("/test").thenReturn("test");

        assertEquals("test", stub.client(stub2.getLocalPort()).resource("/test").non().sync().get().getPayloadString());
        assertEquals("test", stub.client(stub2.getLocalPort()).resource("/test").sync().get().getPayloadString());

        stub.send(stub2.getLocalPort()).token(12345).observation(9, "dupa");
        stub.send(stub2.getLocalPort()).token(12346).observation(9, Code.C404_NOT_FOUND);

        stub2.stop();
    }

    @Test
    public void blockRequestAndResponse() throws IOException, CoapException {
        final String LARGE_BODY = "large payload large payload large payload large payload";

        StubCoapServer stub2 = new StubCoapServer();
        stub2.setBlockSize(BlockSize.S_16);
        stub2.start();
        stub2.whenPUT("/test").withBody(LARGE_BODY).thenReturn(LARGE_BODY);

        assertEquals(LARGE_BODY, stub.client(stub2.getLocalPort()).resource("/test").payload(LARGE_BODY).sync().put().getPayloadString());
        stub2.stop();
    }

    @Test
    public void blockRequest() throws IOException, CoapException {
        final String LARGE_BODY = "large payload large payload large payload large payload";

        StubCoapServer stub2 = new StubCoapServer();
        stub2.setBlockSize(BlockSize.S_16);
        stub2.start();
        stub2.whenPUT("/test").withBody(LARGE_BODY).thenReturn();

        assertEquals(Code.C204_CHANGED, stub.client(stub2.getLocalPort()).resource("/test").payload(LARGE_BODY).sync().put().getCode());
        assertEquals(Code.C400_BAD_REQUEST, stub.client(stub2.getLocalPort()).resource("/test").payload(LARGE_BODY + "d").sync().put().getCode());
        stub2.stop();
    }
}
