/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
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
import org.mbed.coap.packet.MediaTypes;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerObserve;
import org.mbed.coap.server.MessageIdSupplierImpl;
import org.mbed.coap.transmission.SingleTimeout;
import protocolTests.utils.CurrentThreadExecutor;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon
 */
public class BlockTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;

    @Before
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .executor(new CurrentThreadExecutor())
                .observerIdGenerator(new CoapServerObserve.SimpleObservationIDGenerator(0))
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        System.out.println("tearDown -----------");
    }

    @Test
    public void block2() throws Exception {
        transport.when(newCoapPacket(1).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build());

        assertEquals("123456789012345|dupa", client.resource("/path1").get().get().getPayloadString());

    }

    @Test
    public void block2_clientAnticipatesBlockSize() throws Exception {
        transport.when(newCoapPacket(1).get().block2Res(0, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build());

        assertEquals("123456789012345|dupa", client.resource("/path1").blockSize(BlockSize.S_16).get().get().getPayloadString());

    }


    @Test
    public void block1() throws Exception {
        String payload = "123456789012345|123456789012345|dupa";

        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).size1(payload.length()).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_32, true).build());

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_32, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_32, false).build());

        assertEquals(Code.C204_CHANGED, client.resource("/path1").payload(payload, MediaTypes.CT_TEXT_PLAIN).put().get().getCode());

    }

    @Test
    public void block1_separateMode() throws Exception {

        String payload = "123456789012345|123456789012345|dupa";

        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).size1(payload.length()).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(null).build(),
                        newCoapPacket(2).con(Code.C231_CONTINUE).block1Req(0, BlockSize.S_32, true).build());

        //important that this comes first
        transport.when(newCoapPacket(2).ack(null).build()).thenNothing();

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_32, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false).build());

        assertEquals(Code.C204_CHANGED, client.resource("/path1").payload(payload, MediaTypes.CT_TEXT_PLAIN).put().get().getCode());
    }

    @Test
    public void block1_serverChangesBlockSize() throws Exception {

        String payload = "123456789012345|123456789012345|dupa";

        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).size1(payload.length()).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true).build());

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_16, true).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false).build());

        transport.when(newCoapPacket(3).put().block1Req(2, BlockSize.S_16, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(3).ack(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false).build());

        assertEquals(Code.C204_CHANGED, client.resource("/path1").payload(payload, MediaTypes.CT_TEXT_PLAIN).put().get().getCode());

    }

    @Test
    public void block2_notification_success() throws Exception {
        //establish observation
        transport.when(newCoapPacket(1).get().token(1).uriPath("/test").obs(0).build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).token(1).obs(0).payload("12").build());

        ObservationListener observationListener = mock(ObservationListener.class);
        assertEquals("12", client.resource("/test").observe(observationListener).get().getPayloadString());

        //notification with blocks
        System.out.println("------");
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/test").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, true).payload("123456789012345|").build());

        transport.when(newCoapPacket(3).get().block2Res(2, BlockSize.S_16, false).uriPath("/test").build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, false).payload("12345").build());

        transport.receive(newCoapPacket(101).con(Code.C205_CONTENT).obs(2).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build(), new InetSocketAddress("127.0.0.1", 61616));


        verify(observationListener, timeout(1000))
                .onObservation(eq(newCoapPacket(new InetSocketAddress("127.0.0.1", 61616)).mid(2).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, false)
                        .payload("123456789012345|123456789012345|12345").build()));

    }

    @Test
    public void block2_notification_secondBlock_wrongSize() throws Exception {
        //establish observation
        transport.when(newCoapPacket(1).get().token(1).uriPath("/test").obs(0).build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).token(1).obs(0).payload("12").build());

        ObservationListener observationListener = mock(ObservationListener.class);
        assertEquals("12", client.resource("/test").observe(observationListener).get().getPayloadString());

        //notification with blocks
        System.out.println("------");
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/test").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, true).payload("1234567890").build());

        transport.receive(newCoapPacket(101).con(Code.C205_CONTENT).obs(2).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build(), new InetSocketAddress("127.0.0.1", 61616));


        verify(observationListener, never()).onObservation(any(CoapPacket.class));
    }

    @Test
    public void block2_notification_firstBlock_wrongSize() throws Exception {
        //establish observation
        transport.when(newCoapPacket(1).get().token(1).uriPath("/test").obs(0).build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).token(1).obs(0).payload("12").build());

        ObservationListener observationListener = mock(ObservationListener.class);
        assertEquals("12", client.resource("/test").observe(observationListener).get().getPayloadString());

        //notification with blocks
        System.out.println("------");

        transport.receive(newCoapPacket(101).con(Code.C205_CONTENT).obs(1).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345").build(), new InetSocketAddress("127.0.0.1", 61616));


        verify(observationListener, never()).onObservation(any(CoapPacket.class));
    }

}
