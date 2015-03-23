/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import static org.hamcrest.CoreMatchers.instanceOf;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mbed.coap.Code;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.CoapResource;

/**
 * @author szymon
 */
public class CoapServerExecutorTest {

    CoapServer srv;

    @Before
    public void setUp() throws IOException {
        ThreadPoolExecutor es = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(2));

        srv = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(5683)).executor(es).build();
        srv.addRequestHandler("/slow", new SlowResource());
        srv.start();
    }

    final Object monitor = new Object();

    @After
    public void tearDown() {
        srv.stop();
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testQueueFull() throws Throwable {
        CoapClient client1 = CoapClientBuilder.newBuilder(5683).transport(InMemoryTransport.create(1001)).timeout(new SingleTimeout(600)).build();
        CoapClient client2 = CoapClientBuilder.newBuilder(5683).transport(InMemoryTransport.create(1002)).timeout(new SingleTimeout(600)).build();
        CoapClient client3 = CoapClientBuilder.newBuilder(5683).transport(InMemoryTransport.create(1003)).timeout(new SingleTimeout(600)).build();
        CoapClient client4 = CoapClientBuilder.newBuilder(5683).transport(InMemoryTransport.create(1004)).timeout(new SingleTimeout(600)).build();
        CoapClient client5 = CoapClientBuilder.newBuilder(5683).transport(InMemoryTransport.create(1005)).timeout(new SingleTimeout(600)).build();

        CompletableFuture[] cl;
        synchronized (monitor) {
            cl = new CompletableFuture[]{
                    client1.resource("/slow").get(), client2.resource("/slow").get(), client3.resource("/slow").get(),
                    client4.resource("/slow").get(), client5.resource("/slow").get()
            };
        }

        exception.expectCause(instanceOf(CoapTimeoutException.class));
        Arrays.stream(cl).forEach(CompletableFuture::join);
    }

    @Test
    public void testQueueNoTimeout() throws Exception {
        CoapClient client1 = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(InMemoryTransport.create(1001)).timeout(new SingleTimeout(600)).build();
        CoapClient client2 = CoapClientBuilder.newBuilder(InMemoryTransport.createAddress(5683))
                .transport(InMemoryTransport.create(1002)).timeout(new SingleTimeout(600)).build();

        CompletableFuture cl;
        CompletableFuture cl2;

        synchronized (monitor) {
            cl = client1.resource("/slow").get();
            cl2 = client2.resource("/slow").get();
        }

        cl.join();
        cl2.join();
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
