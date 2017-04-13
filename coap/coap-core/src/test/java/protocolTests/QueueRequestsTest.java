/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerObserve;
import com.mbed.coap.server.MessageIdSupplierImpl;
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.transmission.SingleTimeout;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Created by szymon
 */
public class QueueRequestsTest {

    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private CoapTransport transport;
    private CoapClient client;
    private CoapReceiver transportReceiver;
    private CoapServer coapServer;

    @Before
    public void setUp() throws Exception {
        transport = mock(CoapTransport.class);

        coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);

        //capture transport receiver
        ArgumentCaptor<CoapReceiver> transRec = ArgumentCaptor.forClass(CoapReceiver.class);
        verify(transport).start(transRec.capture());
        transportReceiver = transRec.getValue();
    }

    @After
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldSendRequestsToADevice_singleRequest() throws Exception {
        //request
        CompletableFuture<CoapPacket> futResp = client.resource("/path1").get();

        verify(transport).sendPacket(any(), any(), any());

        //send response
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa").build(), null);

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldSendRequestsToADevice_isASequence_2_requests() throws Exception {
        //requests
        CompletableFuture<CoapPacket> futResp1 = client.resource("/path1").get();
        CompletableFuture<CoapPacket> futResp2 = client.resource("/path2").get();

        //only one request should be send
        verify(transport).sendPacket(any(), any(), any());

        //send response
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa1").build(), null);

        //second request should be send
        verify(transport).sendPacket(any(), any(), any());

        //send response
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2").build(), null);


        assertEquals("dupa1", futResp1.get().getPayloadString());
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }


    // all messages transaction priority:   CoapTransaction.Priority.NORMAL
    // block messages transaction priority: CoapTransaction.Priority.HIGH
    @Test
    public void shouldSendRequestsToADevice_isASequence_2_requests_with_block() throws Exception {
        CoapPacket blockResp1 = newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build();
        CoapPacket blockResp2 = newCoapPacket(SERVER_ADDRESS).mid(3).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build();

        //requests
        CompletableFuture<CoapPacket> futResp1 = client.resource("/path1").get(); // makes req with msgId #1 and sends it
        CompletableFuture<CoapPacket> futResp2 = client.resource("/path2").get(); // makes req with msgId #2 and queues it

        //#1 message - block1
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp1, null); // fetches and removes transaction #1, makes block#2 req with msgId #3 and sends it (#2 still in the queue)

        //#2 message - block2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp2, null); // fetches and removes transaction #3, sends queued transaction #2

        assertEquals("123456789012345|dupa", futResp1.get().getPayloadString());

        //#2 message - request2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2").build(), null);

        //verify responses
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }


    // all messages transaction priority:   CoapTransaction.Priority.NORMAL
    // block messages transaction priority: CoapTransaction.Priority.NORMAL
    // (interleave mode)
    @Test
    public void shouldSendRequestsToADevice_isASequence_2_requests_with_block_inserting_message_in_block() throws Exception {
        ((CoapServerObserve) coapServer).setBlockCoapTransactionPriority(CoapTransaction.Priority.NORMAL);

        CoapPacket blockResp1 = newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build();
        CoapPacket blockResp2 = newCoapPacket(SERVER_ADDRESS).mid(3).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build();

        //requests
        CompletableFuture<CoapPacket> futResp1 = client.resource("/block").get();
        CompletableFuture<CoapPacket> futResp2 = client.resource("/path2").get();

        //#1 message - block1
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp1, null);

        //#2 message - request2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2").build(), null);

        //#2 message - block2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp2, null);


        //verify responses
        assertEquals("123456789012345|dupa", futResp1.get().getPayloadString());
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }

    @Test(timeout = 10000)
    public void shouldQueueBlockTransferEvenQueueIsFull() throws Exception {
        coapServer.setEndpointQueueMaximumSize(2);
        ((CoapServerObserve) coapServer).setBlockCoapTransactionPriority(CoapTransaction.Priority.HIGH);

        CoapPacket blockResp1 = newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build();
        CoapPacket blockResp2 = newCoapPacket(SERVER_ADDRESS).mid(3).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build();

        //requests
        CompletableFuture<CoapPacket> futResp1 = client.resource("/block").get(); // adds first request
        CompletableFuture<CoapPacket> futResp2 = client.resource("/path2").get(); // adds second request

        //#1 message - block1 - causes adding to queue extra message from block transfer for a short time (until initial
        // transaction is removed
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp1, null);

        //#1 message - block2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(blockResp2, null);


        //#2 message - request2
        verify(transport).sendPacket(any(), any(), any());
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2").build(), null);


        //verify responses
        assertEquals("123456789012345|dupa", futResp1.get().getPayloadString());
        assertEquals("dupa2", futResp2.get().getPayloadString());
    }

    @Test
    public void test_shouldNotDeleteInactiveTransaction() throws Exception {
        //requests
        CompletableFuture<CoapPacket> futResp1 = client.resource("/path1").get();
        CompletableFuture<CoapPacket> futResp2 = client.resource("/path2").get();

        //only one request should be send
        verify(transport).sendPacket(any(), any(), any());

        //send response for queued inactive transaction
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa2").build(), null);

        verify(transport, never()).sendPacket(any(), any(), any());
        assertFalse(futResp1.isDone());
        assertFalse(futResp2.isDone());

        //send response for first transaction
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(1).ack(Code.C205_CONTENT).payload("dupa1").build(), null);
        //second request should be send
        verify(transport).sendPacket(any(), any(), any());

        assertTrue(futResp1.isDone());
        assertFalse(futResp2.isDone());

        //send response for queued inactive transaction
        reset(transport);
        transportReceiver.handle(newCoapPacket(SERVER_ADDRESS).mid(2).ack(Code.C205_CONTENT).payload("dupa3").build(), null);

        assertTrue(futResp1.isDone());
        assertEquals("dupa1", futResp1.get().getPayloadString());
        assertTrue(futResp2.isDone());
        assertEquals("dupa3", futResp2.get().getPayloadString());

    }
}