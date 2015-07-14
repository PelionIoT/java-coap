/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.testng.Assert.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Method;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transport.InMemoryTransport;
import org.mbed.coap.utils.FutureCallbackAdapter;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class TimeoutTest {

    @Test(expectedExceptions = CoapTimeoutException.class)
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(0))
                .transport(InMemoryTransport.create())
                .timeout(new SingleTimeout(100))
                .build();

        client.resource("/non/existing").sync().get();
    }

    @Test(enabled = false)
    public void timeoutTestIgn() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(61616).executor(Executors.newCachedThreadPool()).build();
        cnn.start();

        CoapPacket request = new CoapPacket(new InetSocketAddress(InetAddress.getLocalHost(), 60666));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        try {
            cnn.makeRequest(request).join();
            fail("Exception was expected");
        } catch (CompletionException ex) {
            //expected
        }
        assertEquals(0, cnn.getNumberOfTransactions(), "Wrong number of transactions");
        cnn.stop();

    }

    @Test
    public void timeoutTest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create())
                .executor(Executors.newCachedThreadPool()).timeout(new SingleTimeout(100)).build();
        cnn.start();

        CoapPacket request = new CoapPacket(InMemoryTransport.createAddress(0));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        try {
            callback.get();
            assertTrue(false, "Exception was expected");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CoapException);
        }
        assertEquals(0, cnn.getNumberOfTransactions(), "Wrong number of transactions");
        cnn.stop();

    }
}
