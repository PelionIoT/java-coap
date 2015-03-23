/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.junit.Assert.assertEquals;
import static org.mbed.coap.server.CoapServerObserve.SimpleObservationIDGenerator;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.client.ObservationListener;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.server.MessageIdSupplierImpl;
import org.mockito.ArgumentMatcher;
import protocolTests.utils.CurrentThreadExecutor;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon
 */
public class ObservationWithBlockTest {


    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;
    private ObservationListener observationListener;

    @Before
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServerBuilder.newBuilder().transport(transport).midSupplier(new MessageIdSupplierImpl(0))
                .executor(new CurrentThreadExecutor())
                .observerIdGenerator(new SimpleObservationIDGenerator(0)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);

        //establish observation relation
        transport.when(newCoapPacket(1).get().uriPath("/path1").obs(0).token(1).build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).obs(0).token(1).payload("12345").build());

        observationListener = mock(ObservationListener.class);
        assertEquals("12345", client.resource("/path1").sync().observe(observationListener).getPayloadString());
        reset(observationListener);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void blockObservation_noEtag() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT).obs(1).token(1).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener).onObservation(hasPayload("perse perse perse perse"));
    }

    @Test
    public void blockObservation_withEtag() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).etag(12).block2Res(1, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT).obs(1).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener).onObservation(hasPayload("perse perse perse perse"));
    }

    @Test
    public void blockObservation_etagChanges() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).etag(13).block2Res(1, BlockSize.S_16, false).payload("dupa dupa").build());

        //send observation with block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT).obs(1).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
        verify(observationListener, never()).onTermination(any(CoapPacket.class));
    }

    @Test
    public void blockObservation_errorResponse() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C400_BAD_REQUEST).build());

        //send observation with block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT).obs(1).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
        verify(observationListener, never()).onTermination(any(CoapPacket.class));
    }

    public static class HasPayloadMatcher extends ArgumentMatcher<CoapPacket> {

        private final String expectedPayload;

        public HasPayloadMatcher(String expectedPayload) {
            this.expectedPayload = expectedPayload;
        }

        @Override
        public boolean matches(Object o) {
            return expectedPayload.equals(((CoapPacket) o).getPayloadString());
        }
    }

    public static CoapPacket hasPayload(String expectedPayload) {
        return argThat(new HasPayloadMatcher(expectedPayload));
    }
}
