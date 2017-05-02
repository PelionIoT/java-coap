/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests;

import static com.mbed.coap.server.CoapServerObserve.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.ObservationWithBlockTest.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.client.ObservationListener;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.MessageIdSupplierImpl;
import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon
 */
public class Observation2Test {


    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;
    private ObservationListener observationListener;

    @Before
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServerBuilder.newBuilder().transport(transport).midSupplier(new MessageIdSupplierImpl(0))
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
    public void shouldReceiveEmptyAckAfterObservation() throws Exception {
        //send observation
        transport.receive(newCoapPacket(3).con(Code.C205_CONTENT).obs(1).token(1).payload("perse perse").build(), SERVER_ADDRESS);

        //important, no token included in response
        assertEquals(transport.getLastOutgoingMessage(), newCoapPacket(3).ack(null).build());
        verify(observationListener).onObservation(hasPayload("perse perse"));
    }
}
