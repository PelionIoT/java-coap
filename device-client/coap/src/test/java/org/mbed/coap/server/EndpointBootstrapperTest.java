/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.EndpointBootstrapper.BootstrappingState;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.FutureCallbackAdapter;

/**
 * @author nordav01
 */
public class EndpointBootstrapperTest {

    private final static String EP_NAME = "ep1";
    private final static int BS_PORT = 61616;

    private CoapServer epServer;
    private CoapServer bsServer;

    @Before
    public void setUp() throws IOException {
        epServer = CoapServerBuilder.newBuilder()
                .transport(InMemoryTransport.create(CoapConstants.DEFAULT_PORT))
                .timeout(new SingleTimeout(2000))
                .build()
                .start();

        bsServer = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(BS_PORT)).build().start();
    }

    private void setupBootstrapRequestHandler(CoapPacket... responses) {
        final LinkedList<CoapPacket> responseList = new LinkedList<>(Arrays.asList(responses));
        bsServer.addRequestHandler("/bs", new CoapHandler() {
            @Override
            public void handle(CoapExchange exchange) throws CoapException {
                exchange.setResponseCode(Code.C204_CHANGED);
                exchange.sendResponse();

                while (!responseList.isEmpty()) {
                    bsServer.makeRequest(responseList.removeFirst(), new Callback<CoapPacket>() {
                        @Override
                        public void call(CoapPacket t) {
//                            System.out.println(t);
                        }

                        @Override
                        public void callException(Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            }
        });
    }

    private void setupBootstrapRequestErrorHandler(final Code code) {
        bsServer.addRequestHandler("/bs", exchange -> {
            exchange.setResponseCode(code);
            exchange.sendResponse();
        });
    }

    private static CoapPacket createCoap(String uri, String payload, short contentType) {
        InetSocketAddress clientAddr = new InetSocketAddress("localhost", CoapConstants.DEFAULT_PORT);
        CoapPacket packet = new CoapPacket(Method.PUT, MessageType.Confirmable, uri, clientAddr);
        packet.setPayload(payload);
        packet.headers().setContentFormat(contentType);
        return packet;
    }

    @Test
    public void successfulNoSecBootstrap() throws Exception {
        CoapPacket response = createCoap("/0/1/0", "coap://localhost:5673", MediaTypes.CT_APPLICATION_LWM2M_TEXT);
        setupBootstrapRequestHandler(response);

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        boot.setType("Test");
        boot.setDomain("domain");
        assertNull(boot.getDsAddress());
        FutureCallbackAdapter<BootstrappingState> callback = new FutureCallbackAdapter<>();
        boot.bootstrap(callback);
        waitFor(callback, BootstrappingState.BOOTSTRAPPED, 1000);
        assertEquals(BootstrappingState.BOOTSTRAPPED, boot.getState());
        assertEquals(new InetSocketAddress("localhost", 5673), boot.getDsAddress());
    }

    @Test
    public void successfulNoSecBootstrapWithDomain() throws Exception {
        setupBootstrapRequestHandler(
                createCoap("/0/1/366", "mbed.org", MediaTypes.CT_APPLICATION_LWM2M_TEXT),
                createCoap("/0/1/0", "coap://localhost:5673", MediaTypes.CT_APPLICATION_LWM2M_TEXT));

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        boot.setType("Test");
        assertNull(boot.getDsAddress());
        FutureCallbackAdapter<BootstrappingState> callback = new FutureCallbackAdapter<>();
        boot.bootstrap(callback);
        waitFor(callback, BootstrappingState.BOOTSTRAPPED, 1000);
        assertEquals(BootstrappingState.BOOTSTRAPPED, boot.getState());
        assertEquals(new InetSocketAddress("localhost", 5673), boot.getDsAddress());
        assertEquals("mbed.org", boot.getDomain());
    }

    @Test
    public void successfulNoSecBootstrapWithNoCallback() throws Exception {
        CoapPacket response1 = createCoap("/0/0/0", "coap://localhost:5673", MediaTypes.CT_APPLICATION_LWM2M_TEXT);
        response1.setMethod(Method.DELETE);
        CoapPacket response2 = createCoap("/0/0/0", "coap://localhost:5673", MediaTypes.CT_APPLICATION_LWM2M_TEXT);
        setupBootstrapRequestHandler(response1, response2);

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        assertNull(boot.getDsAddress());
        boot.bootstrap();

        int i = 0;
        while (boot.getState() != BootstrappingState.BOOTSTRAPPED && i < 50) {
            Thread.sleep(20);
        }

        assertEquals(BootstrappingState.BOOTSTRAPPED, boot.getState());
        assertEquals(new InetSocketAddress("localhost", 5673), boot.getDsAddress());
    }

    @Test
    public void failedBootstrapRequest() throws Exception {
        setupBootstrapRequestErrorHandler(Code.C400_BAD_REQUEST);

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        FutureCallbackAdapter<BootstrappingState> callback = new FutureCallbackAdapter<>();
        boot.bootstrap(callback);
        waitFor(callback, BootstrappingState.BOOTSTRAP_FAILED, 1000);
        assertEquals(BootstrappingState.BOOTSTRAP_FAILED, boot.getState());
        assertNull(boot.getDsAddress());
    }

    @Test
    public void bootstrapWithNotAcceptabeContentType() throws Exception {
        CoapPacket response = createCoap("/0/0/0", "{ coap://localhost:5673 }", MediaTypes.CT_APPLICATION_LWM2M_JSON);
        setupBootstrapRequestHandler(response);

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        FutureCallbackAdapter<BootstrappingState> callback = new FutureCallbackAdapter<>();
        boot.bootstrap(callback);
        waitFor(callback, BootstrappingState.BOOTSTRAP_FAILED, 1000);
        assertEquals(BootstrappingState.BOOTSTRAP_REQUESTED, boot.getState());
        assertNull(boot.getDsAddress());
    }

    @Test
    public void successfulBootstrapWithFallback() throws Exception {
        setupBootstrapRequestHandler(
                createCoap("/0/0/0", "{ coap://localhost:5673 }", MediaTypes.CT_APPLICATION_LWM2M_JSON),
                createCoap("/0/0/0", "coap://localhost:5673", MediaTypes.CT_APPLICATION_LWM2M_TEXT));

        EndpointBootstrapper boot = new EndpointBootstrapper(epServer, InMemoryTransport.createAddress(BS_PORT), EP_NAME);
        FutureCallbackAdapter<BootstrappingState> callback = new FutureCallbackAdapter<>();
        boot.bootstrap(callback);
        waitFor(callback, BootstrappingState.BOOTSTRAPPED, 1000);
        assertEquals(BootstrappingState.BOOTSTRAPPED, boot.getState());
        assertEquals(new InetSocketAddress("localhost", 5673), boot.getDsAddress());
    }

    private static void waitFor(FutureCallbackAdapter<BootstrappingState> callback, BootstrappingState desired, long timeout)
            throws InterruptedException, ExecutionException {
        long millis = 20;
        int retry = (int) (timeout / millis) + 1;
        while (callback.get() != desired && retry-- > 0) {
            Thread.sleep(millis);
        }
    }

    @After
    public void tearDown() {
        epServer.stop();
        bsServer.stop();
    }

}
