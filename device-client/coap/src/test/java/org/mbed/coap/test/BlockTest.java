/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import static org.junit.Assert.assertEquals;
import static org.mbed.coap.test.CoapPacketBuilder.newCoapPacket;
import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.BlockSize;
import org.mbed.coap.Code;
import org.mbed.coap.MediaTypes;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.server.CoapIdContextImpl;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.server.CoapServerObserve;

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

        CoapServer coapServer = CoapServerBuilder.newBuilder().transport(transport).context(new CoapIdContextImpl(0)).blockSize(BlockSize.S_32)
                .executor(new CurrentThreadExecutor())
                .observerIdGenerator(new CoapServerObserve.SimpleObservationIDGenerator(0)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
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
        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_32, true).build());

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_32, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_32, false).build());

        assertEquals(Code.C204_CHANGED, client.resource("/path1").payload("123456789012345|123456789012345|dupa", MediaTypes.CT_TEXT_PLAIN).put().get().getCode());

    }

    @Test
    public void block1_serverChangesBlockSize() throws Exception {
        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_16, true).build());

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_16, true).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_16, false).build());

        transport.when(newCoapPacket(3).put().block1Req(2, BlockSize.S_16, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(3).ack(Code.C204_CHANGED).block1Req(2, BlockSize.S_16, false).build());

        assertEquals(Code.C204_CHANGED, client.resource("/path1").payload("123456789012345|123456789012345|dupa", MediaTypes.CT_TEXT_PLAIN).put().get().getCode());

    }
}
