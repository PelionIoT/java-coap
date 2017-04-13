/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.junit.Assert.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author szymon
 */
public class TimeoutTest {

    @Test(expected = CoapTimeoutException.class)
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapClientBuilder.newBuilder(InMemoryCoapTransport.createAddress(0))
                .transport(InMemoryCoapTransport.create())
                .timeout(new SingleTimeout(100))
                .build();

        client.resource("/non/existing").sync().get();
    }

    @Test
    @Ignore
    public void timeoutTestIgn() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(61616, Executors.newCachedThreadPool()).build();
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
        assertEquals("Wrong number of transactions", 0, cnn.getNumberOfTransactions());
        cnn.stop();

    }

    @Test
    public void timeoutTest() throws Exception {
        CoapServer cnn = CoapServerBuilder.newBuilder().transport(InMemoryCoapTransport.create()).timeout(new SingleTimeout(100)).build();
        cnn.start();

        CoapPacket request = new CoapPacket(InMemoryCoapTransport.createAddress(0));
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        try {
            callback.get();
            fail("Exception was expected");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CoapException);
        }
        assertEquals("Wrong number of transactions", 0, cnn.getNumberOfTransactions());
        cnn.stop();

    }
}
