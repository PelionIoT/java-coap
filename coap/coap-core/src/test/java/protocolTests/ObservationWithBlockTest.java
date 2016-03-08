/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.mbed.coap.server.CoapServerObserve.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import java.net.InetSocketAddress;
import org.hamcrest.Description;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.client.ObservationListener;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MediaTypes;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.CoapServerBuilder;
import org.mbed.coap.server.MessageIdSupplierImpl;
import org.mockito.ArgumentMatcher;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
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

    @BeforeMethod
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

    @AfterMethod
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void blockObservation_noEtag() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(101).ack(Code.C205_CONTENT).obs(1).token(1).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener).onObservation(hasPayload("perse perse perse perse"));
    }

    @Test
    public void blockObservation_multipleBlocks() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, true).payload(" ----- 16 ------").build());

        transport.when(newCoapPacket(3).get().uriPath("/path1").block2Res(2, BlockSize.S_16, false).build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(101).ack(Code.C205_CONTENT).obs(1).token(1).payload("------ 16 ------").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener).onObservation(hasPayload("------ 16 ------ ----- 16 ------e perse"));
    }

    @Test
    public void blockObservation_singleBlock() throws Exception {
        //send observation with single last block
        transport.receive(newCoapPacket(101).ack(Code.C205_CONTENT).obs(1).token(1).payload("perse perse").block2Res(0, BlockSize.S_16, false).build(), SERVER_ADDRESS);

        verify(observationListener).onObservation(hasPayload("perse perse"));
    }

    @Test
    public void blockObservation_noEtagDuplicateFirstBlock() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());
        transport.when(newCoapPacket(3).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build());

        transport.receive(newCoapPacket(10).ack(Code.C205_CONTENT).obs(1).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
        verify(observationListener, never()).onTermination(any(CoapPacket.class));
    }

    @Test
    public void blockObservation_noEtagDuplicateFirstMultipleTimes() throws Exception {
        int maxDuplicates = 10;

        for (int i = 2; i < maxDuplicates; i++) {
            transport.when(newCoapPacket(i).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                    .then(newCoapPacket(i).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());
        }
        transport.when(newCoapPacket(maxDuplicates).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(maxDuplicates).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build());

        transport.receive(newCoapPacket(10).ack(Code.C205_CONTENT).obs(1).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
        verify(observationListener, never()).onTermination(any(CoapPacket.class));
    }

    private String makeBlock(int msgId, int blockNum, boolean hasMore, String payload) {
        transport.when(newCoapPacket(msgId).get().uriPath("/path1").block2Res(blockNum, BlockSize.S_16, false).build())
                .then(newCoapPacket(msgId).ack(Code.C205_CONTENT).block2Res(blockNum, BlockSize.S_16, hasMore).payload(payload).build());
        return payload;
    }

    @Test
    public void blockObservation_noEtagManyBlocks() throws Exception {
        // can fail with StackOverflowException if used CurrentThreadExecutor for test (see setUp() method) and blocks count bigger than 300 blocks (becomes flaky above 300 blocks)
        int maxBlocks = 200;
        StringBuilder expectedPayload = new StringBuilder();
        expectedPayload.append("|block_0000_____");

        for (int i = 1; i < maxBlocks; i++) {
            // msgId and block num is "thin system of ropes" - msgId should start from 2 (it will be first GET request), blockNum from 1 (we send block 0 in initial notification)
            expectedPayload.append(makeBlock(i + 1, i, true, String.format("|block_%04d_____", i)));
        }
        expectedPayload.append(makeBlock(maxBlocks + 1, maxBlocks, false, "|last_block"));

        transport.receive(newCoapPacket(10).ack(Code.C205_CONTENT).obs(1).token(1).block2Res(0, BlockSize.S_16, true).payload("|block_0000_____").build(), SERVER_ADDRESS);
        // if default executor is used (asynchronous) - uncomment this line
        //        Thread.sleep(10000);
        verify(observationListener).onObservation(hasPayload(expectedPayload.toString()));
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

    @Test
    public void blockObservation_wrongPayloadSize_firstBlock() throws Exception {
        //send observation with too short block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT)
                .obs(1).token(1).payload("------ 15 ----x").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));

        //send observation with too long block
        reset(observationListener);
        transport.receive(newCoapPacket(4).ack(Code.C205_CONTENT)
                .obs(1).token(1).payload("------ 17 ------e").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
    }

    @Test
    public void blockObservation_wrongPayloadSize_secondBlock() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).contFormat(MediaTypes.CT_TEXT_PLAIN).block2Res(1, BlockSize.S_16, true).payload("------ 15 ----x").build());

        //send observation with block
        transport.receive(newCoapPacket(3).ack(Code.C205_CONTENT).contFormat(MediaTypes.CT_TEXT_PLAIN)
                .obs(1).token(1).payload("------ 16 ------").block2Res(0, BlockSize.S_16, true).build(), SERVER_ADDRESS);

        verify(observationListener, never()).onObservation(any(CoapPacket.class));
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

        @Override
        public void describeTo(Description description) {
            description.appendText(expectedPayload);
        }
    }

    public static CoapPacket hasPayload(String expectedPayload) {
        return argThat(new HasPayloadMatcher(expectedPayload));
    }
}
