/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.MessageIdSupplierImpl;
import com.mbed.coap.transmission.SingleTimeout;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon.
 */
public class SeparateResponseTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;

    @Before
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldResponseWithEmptyAckAndSeparateResponse() throws Exception {
        //empty ack
        transport.when(newCoapPacket(1).token(123).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(null).build());

        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response
        transport.receive(newCoapPacket(2).token(123).non(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponse_withoutEmptyAck() throws Exception {
        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response, no empty ack
        transport.receive(newCoapPacket(2).token(123).con(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);
        assertEquals("dupa", futResp.get().getPayloadString());
    }
}
