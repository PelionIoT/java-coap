package org.mbed.coap.server;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.CoapConstants;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.linkformat.LinkFormatBuilder;
import org.mbed.coap.test.CoapResourceMock;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.utils.FutureCallbackAdapter;
import org.mbed.coap.utils.SimpleCoapResource;

/**
 *
 * @author szymon
 */
public class EndPointRegistratorTest {

    private CoapServer epServer;
    private CoapServer rdServer;
    private final static int RD_PORT = 61616;
    private final static String EP_NAME = "ep1";
    private final CoapResourceMock rdResource = new CoapResourceMock();

    @Before
    public void setUp() throws IOException {
        epServer = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(CoapConstants.DEFAULT_PORT))
                .timeout(new SingleTimeout(2000)).build();
        epServer.addRequestHandler("/dev/temp", new SimpleCoapResource("21", "ucum:Cel"));
        epServer.addRequestHandler("/dev/power", new SimpleCoapResource("100", "ucum:Pwr"));
        epServer.start();

        rdServer = CoapServerBuilder.newBuilder().transport(InMemoryTransport.create(RD_PORT)).build();
        rdServer.addRequestHandler("/rd", rdResource);
        rdServer.addRequestHandler("/rd/*", rdResource);
        rdServer.start();
    }

    @After
    public void tearDown() {
        epServer.stop();
        rdServer.stop();
    }

    @Test
    public void testSuccesfullRegistration() throws InterruptedException, ExecutionException {
        CoapPacket result = new CoapPacket(Code.C201_CREATED, MessageType.Acknowledgement, null);
        result.headers().setLocationPath("/rd/" + EP_NAME);
        rdResource.mockResponse(Method.POST, result);
        rdResource.mockResponse(Method.DELETE, new CoapPacket(Code.C202_DELETED, MessageType.Acknowledgement, null));
        rdResource.mockResponse(Method.PUT, new CoapPacket(Code.C204_CHANGED, MessageType.Acknowledgement, null));

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setDomain("domain");
        epReg.setHostName(EP_NAME);
        epReg.setType("test");

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());

        //UPDATE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        fut.get();
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, epReg.getState());
        assertEquals(0, rdResource.getLatestRequest().getPayload().length);

        //REMOVE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.unregister(fut);
        assertEquals(EndPointRegistrator.RegistrationState.NOT_REGISTERED, fut.get());

    }

    @Test
    public void testSuccesfullRegistrationWithTemplate() throws InterruptedException, ExecutionException {
        CoapPacket result = new CoapPacket(Code.C201_CREATED, MessageType.Acknowledgement, null);
        result.headers().setLocationPath("/rd/" + EP_NAME);
        rdResource.mockResponse(Method.POST, result);
        rdResource.mockResponse(Method.DELETE, new CoapPacket(Code.C202_DELETED, MessageType.Acknowledgement, null));
        rdResource.mockResponse(Method.PUT, new CoapPacket(Code.C204_CHANGED, MessageType.Acknowledgement, null));

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setEnableTemplate(true);
        epReg.setDomain("domain");
        epReg.setHostName(EP_NAME);
        epReg.setType("test");

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());
        assertEquals(0, rdResource.getLatestRequest().getPayload().length);

        //UPDATE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        fut.get();
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, epReg.getState());
        assertEquals(0, rdResource.getLatestRequest().getPayload().length);

        //REMOVE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.unregister(fut);
        assertEquals(EndPointRegistrator.RegistrationState.NOT_REGISTERED, fut.get());

    }

    @Test
    public void testFailedRegistration() throws InterruptedException, ExecutionException {
        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setRdPath("/non-existing");

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.FAILED, fut.get());

    }

    @Test
    public void testFailedRegistrationTimeout() throws InterruptedException {
        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(0), EP_NAME);
        epServer.setResponseTimeout(new SingleTimeout(400));

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        synchronized (rdResource) {
            epReg.register(fut);
            assertEquals(EndPointRegistrator.RegistrationState.REGISTRATION_SENT, epReg.getState());
        }
        try {
            fut.get();
            assertTrue("Timeout Exception expected", true);
        } catch (ExecutionException ex) {
            //expected
        }
        assertEquals(EndPointRegistrator.RegistrationState.FAILED, epReg.getState());

    }

    @Test
    public void testRegistrationMessage() throws InterruptedException, ExecutionException, ParseException {
        CoapPacket result = new CoapPacket(Code.C201_CREATED, MessageType.Acknowledgement, null);
        result.headers().setLocationPath("/rd/" + EP_NAME);
        rdResource.mockResponse(Method.POST, result);

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setDomain("domain");
        epReg.setHostName(EP_NAME);
        epReg.setType("test-type");
        epReg.setInstance("test-inst");
        epReg.setLifeTime(1234);
        epReg.setContext("test-context");

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());

        assertTrue(rdResource.getLatestRequest().getPayload().length > 0);
        assertEquals("/rd", rdResource.getLatestRequest().headers().getUriPath());
        assertEquals(epReg.getDomain(), rdResource.getLatestRequest().headers().getUriHost());

        Map<String, String> uriQuery = rdResource.getLatestRequest().headers().getUriQueryMap();
        assertNotNull(uriQuery);
        assertEquals(EP_NAME, uriQuery.get("ep"));
        assertEquals("1234", uriQuery.get("lt"));
        assertEquals("test-type", uriQuery.get("et"));
        assertEquals("test-inst", uriQuery.get("ins"));
        assertEquals("test-context", uriQuery.get("con"));

    }

    @Test
    public void testRegistrationTimeout() throws InterruptedException, ExecutionException {
        CoapPacket result = new CoapPacket(Code.C201_CREATED, MessageType.Acknowledgement, null);
        result.headers().setLocationPath("/rd/" + EP_NAME);
        rdResource.mockResponse(Method.POST, result);

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setLifeTime(1);

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());
        assertFalse(epReg.isTimeout());
        Thread.sleep(1100);
        assertTrue(epReg.isTimeout());

        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());

    }

    @Test
    public void testUnregisterForNotRegistered() throws InterruptedException, ExecutionException {
        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.unregister(fut);
        assertEquals(EndPointRegistrator.RegistrationState.NOT_REGISTERED, fut.get());

    }

    @Test
    public void testUpdateRegistrationWithExtraResources() throws InterruptedException, ExecutionException, ParseException {
        CoapPacket result = new CoapPacket(Code.C201_CREATED, MessageType.Acknowledgement, null);
        result.headers().setLocationPath("/rd/" + EP_NAME);
        rdResource.mockResponse(Method.POST, result);
        rdResource.mockResponse(Method.DELETE, new CoapPacket(Code.C202_DELETED, MessageType.Acknowledgement, null));
        rdResource.mockResponse(Method.PUT, new CoapPacket(Code.C204_CHANGED, MessageType.Acknowledgement, null));

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setDomain("domain");
        epReg.setHostName(EP_NAME);
        epReg.setType("test");

        FutureCallbackAdapter<EndPointRegistrator.RegistrationState> fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, fut.get());
        assertEquals(Method.POST, rdResource.getLatestRequest().getMethod());

        //ADD RESOURCE TO ENDPOINT
        epServer.addRequestHandler("/extra", new SimpleCoapResource("extra", "ns:test"));

        //UPDATE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        fut.get();
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, epReg.getState());

        assertNotNull(rdResource.getLatestRequest().getPayloadString());
        assertEquals(Method.PUT, rdResource.getLatestRequest().getMethod());
        List<LinkFormat> listLF = LinkFormatBuilder.parseLinkAsList(rdResource.getLatestRequest().getPayloadString());
        assertFalse(listLF.isEmpty());
        assertEquals(1, listLF.size());
        assertEquals("/extra", listLF.get(0).getUri());
        assertEquals("ns:test", listLF.get(0).getResourceTypeArray()[0]);

        //SECOND UPDATE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        fut.get();
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, epReg.getState());
        assertNull(rdResource.getLatestRequest().getPayloadString());
        assertEquals(Method.PUT, rdResource.getLatestRequest().getMethod());

        //ADD RESOURCES TO ENDPOINT
        epServer.addRequestHandler("/extra", new SimpleCoapResource("extra1", "ns:te2"));
        epServer.addRequestHandler("/extra2", new SimpleCoapResource("extra2", "ns:te2"));

        //UPDATE REGISTRATION
        fut = new FutureCallbackAdapter<>();
        epReg.register(fut);
        fut.get();
        assertEquals(EndPointRegistrator.RegistrationState.REGISTERED, epReg.getState());

        assertEquals(Method.PUT, rdResource.getLatestRequest().getMethod());
        assertNotNull(rdResource.getLatestRequest().getPayloadString());
        listLF = LinkFormatBuilder.parseLinkAsList(rdResource.getLatestRequest().getPayloadString());
        assertFalse(listLF.isEmpty());
        assertEquals(2, listLF.size());
    }

    @Test
    public void testDithering() {
        int min = 10;
        int max = 20;
        int lifeTimeSec = 600; // 10 minutes
        int retryCount = 10;

        EndPointRegistrator epReg = new EndPointRegistrator(epServer, InMemoryTransport.createAddress(RD_PORT), EP_NAME);
        epReg.setLifeTime(lifeTimeSec);
        epReg.setRegisterLimits(min, max, 20);
        assertEquals(0, epReg.getCurrentRetryCount());

        int ditherCap = max - min + 1;

//        for (int j = 0 ; j < 1000; j++) {
        boolean[] haveValues = new boolean[ditherCap];
        for (int i = 0; i < 100000; i++) {
            haveValues[epReg.getRegisterRandomDithering() - min] = true;
        }
        for (int i = 0; i < haveValues.length; i++) {
            assertTrue(haveValues[i]);
        }
//        }

        epReg.setCurrentRetryCount(retryCount);
        haveValues = new boolean[ditherCap];
        for (int i = 0; i < 100000; i++) {
            haveValues[epReg.getRegisterRandomDithering() - (retryCount * retryCount * max + min)] = true;
        }
        for (int i = 0; i < haveValues.length; i++) {
            assertTrue(haveValues[i]);
        }

        assertEquals(retryCount, epReg.getCurrentRetryCount());

        haveValues = new boolean[ditherCap];
        for (int i = 0; i < 100000; i++) {
            long reReg = epReg.getReregisterDelay(); // milliseconds
            int reRegSecs = (int) reReg / 1000;
            //System.out.println("to index : "+ (reRegSecs + (30 - lifeTimeSec) + ditherCap - 1));
            haveValues[reRegSecs + (30 - lifeTimeSec) + ditherCap - 1] = true; // traverse to array index
        }
        for (int i = 0; i < haveValues.length; i++) {
            assertTrue("failed with i:" + i, haveValues[i]);
        }

        epReg.setLifeTime(19);
        epReg.setRegisterLimits(0, 20, 0);
        assertEquals(20000, epReg.getReregisterDelay());
    }
}
