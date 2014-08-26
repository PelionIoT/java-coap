package org.mbed.coap.server;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.SyncCallback;

/**
 *
 * @author szymon
 */
public class CoapServerExecutorTest {

    CoapServer srv;

    @Before
    public void setUp() throws IOException {
        ThreadPoolExecutor es = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(2));

        srv = CoapServer.newBuilder().transport(InMemoryTransport.create(5683)).executor(es).build();
        srv.addRequestHandler("/slow", new SlowResource());
        srv.start();
    }
    final Object monitor = new Object();
    final Object monitor2 = new Object();
    AtomicInteger atomicInt = new AtomicInteger(0);

    @After
    public void tearDown() {
        srv.stop();
    }

    @Test(expected = CoapTimeoutException.class)
    public void testQueueFull() throws IOException, CoapException, Exception {
        CoapClient client1 = CoapClient.newBuilder(5683).transport(InMemoryTransport.create(1001)).timeout(new SingleTimeout(600)).build();
        CoapClient client2 = CoapClient.newBuilder(5683).transport(InMemoryTransport.create(1002)).timeout(new SingleTimeout(600)).build();
        CoapClient client3 = CoapClient.newBuilder(5683).transport(InMemoryTransport.create(1003)).timeout(new SingleTimeout(600)).build();
        CoapClient client4 = CoapClient.newBuilder(5683).transport(InMemoryTransport.create(1004)).timeout(new SingleTimeout(600)).build();
        CoapClient client5 = CoapClient.newBuilder(5683).transport(InMemoryTransport.create(1005)).timeout(new SingleTimeout(600)).build();

        SyncCallback<CoapPacket> cl = new SyncCallback<>();
        SyncCallback<CoapPacket> cl2 = new SyncCallback<>();
        SyncCallback<CoapPacket> cl3 = new SyncCallback<>();
        SyncCallback<CoapPacket> cl4 = new SyncCallback<>();
        SyncCallback<CoapPacket> cl5 = new SyncCallback<>();

        synchronized (monitor) {
            client1.resource("/slow").get(cl);
            client2.resource("/slow").get(cl2);
            client3.resource("/slow").get(cl3);
            client4.resource("/slow").get(cl4);
            client5.resource("/slow").get(cl5);
        }

        cl.getAndClear();
        cl2.getAndClear();
        cl3.getAndClear();
        cl4.getAndClear();
        cl5.getAndClear();
    }

    @Test
    public void testQueueNoTimeout() throws IOException, CoapException, Exception {
        CoapClient client1 = CoapClient.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(InMemoryTransport.create(1001)).timeout(new SingleTimeout(600)).build();
        CoapClient client2 = CoapClient.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(InMemoryTransport.create(1002)).timeout(new SingleTimeout(600)).build();

        SyncCallback<CoapPacket> cl = new SyncCallback<>();
        SyncCallback<CoapPacket> cl2 = new SyncCallback<>();

        synchronized (monitor) {
            client1.resource("/slow").get(cl);
            client2.resource("/slow").get(cl2);
        }

        cl.getAndClear();
        cl2.getAndClear();
    }

    private class SlowResource extends CoapResource {

        @Override
        public void get(CoapExchange ex) throws CoapCodeException {
            synchronized (monitor) {
                ex.setResponseCode(Code.C205_CONTENT);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex1) {
                Logger.getLogger(CoapServerExecutorTest.class.getName()).log(Level.SEVERE, null, ex1);
            }
            ex.send();
        }
    }
}
