package org.mbed.coap.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Method;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.FutureCallbackAdapter;
import org.mbed.coap.utils.SyncCallback;

/**
 *
 * @author szymon
 */
public class TimeoutTest {

    @Test(expected = CoapTimeoutException.class)
    public void testTimeout() throws IOException, CoapException {
        CoapClient client = CoapClient.newBuilder(InMemoryTransport.createAddress(0))
                .transport(InMemoryTransport.create())
                .timeout(new SingleTimeout(100))
                .build();

        client.resource("/non/existing").sync().get();
    }

    @Test
    @Ignore
    public void timeoutTestIgn() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServer.newBuilder().transport(61616).executor(Executors.newCachedThreadPool()).build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.setAddress(new InetSocketAddress(InetAddress.getLocalHost(), 60666));

        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        cnn.makeRequest(request, callback);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        try {
            callback.getResponse();
            assertTrue("Exception was expected", false);
        } catch (CoapException ex) {
            //expected
        }
        assertEquals("Wrong number of transactions", 0, cnn.getNumberOfTransactions());
        cnn.stop();

    }

    @Test
    public void timeoutTest() throws CoapException, UnknownHostException, IOException, InterruptedException, Exception {
        CoapServer cnn = CoapServer.newBuilder().transport(InMemoryTransport.create())
                .executor(Executors.newCachedThreadPool()).timeout(new SingleTimeout(100)).build();
        cnn.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.setAddress(InMemoryTransport.createAddress(0));

        FutureCallbackAdapter<CoapPacket> callback = new FutureCallbackAdapter<>();
        cnn.makeRequest(request, callback);

        //assertEquals("Wrong number of transactions", 1, cnn.getNumberOfTransactions());
        try {
            callback.get();
            assertTrue("Exception was expected", false);
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CoapException);
        }
        assertEquals("Wrong number of transactions", 0, cnn.getNumberOfTransactions());
        cnn.stop();

    }
}
