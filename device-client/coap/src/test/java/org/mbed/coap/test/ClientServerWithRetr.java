package org.mbed.coap.test;

import org.mbed.coap.server.CoapServerBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Method;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.SimpleCoapResource;

/**
 *
 * @author szymon
 */
public class ClientServerWithRetr {

    CoapServer server = null;

    @Before
    public void setUp() throws IOException {
        server = CoapServerBuilder.newBuilder().transport(CoapConstants.DEFAULT_PORT).executor(Executors.newCachedThreadPool()).build();
        server.addRequestHandler("/test/1", new SimpleCoapResource("Dziala"));
        //server.addRequestHandler("/test2", new TestResource());
        //server.addRequestHandler("/bigResource", new BigResource() );
        server.start();

    }

    @Test
    @Ignore
    public synchronized void runTest() throws Exception {
        CoapServer cnnServer = CoapServerBuilder.newBuilder().transport(61616).executor(Executors.newCachedThreadPool()).build();
        cnnServer.start();

        CoapPacket request = new CoapPacket();
        request.setMethod(Method.GET);
        request.headers().setUriPath("/test/1");
        request.setMessageId(1647);
        request.setRemoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), CoapConstants.DEFAULT_PORT + 1));

        CallbackImpl callback = new CallbackImpl();
        cnnServer.makeRequest(request, callback);

        assertEquals("Dziala", callback.eventualPayloadResp());

    }

    private static class CallbackImpl implements Callback<CoapPacket> {

        String payloadResp = null;
        private Exception ex;

        synchronized String eventualPayloadResp() throws Exception {
            while (payloadResp == null && ex == null) {
                wait();
            }
            if (ex != null) {
                throw ex;
            }
            return payloadResp;

        }

        @Override
        public synchronized void call(CoapPacket t) {
            payloadResp = t.getPayloadString();
            notifyAll();
        }

        @Override
        public synchronized void callException(Exception ex) {
            this.ex = ex;
            notifyAll();
        }
    }
}
