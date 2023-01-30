/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package protocolTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import com.mbed.coap.utils.ObservationConsumer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.TransportConnectorMock;


public class ObservationWithBlockTest {


    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;
    private ObservationConsumer observationListener;

    @BeforeEach
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        client = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0))
                .blockSize(BlockSize.S_16)
                .buildClient(SERVER_ADDRESS);

        //establish observation relation
        transport.when(newCoapPacket(1).get().uriPath("/path1").obs(0).token(1).build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).obs(0).token(1).payload("12345").build());

        observationListener = new ObservationConsumer();
        assertEquals("12345", client.observe("/path1", Opaque.ofBytes(1), observationListener).join().getPayloadString());
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void blockObservation_noEtag() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(101).con(Code.C205_CONTENT).obs(2).token(1).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build());

        assertEquals(observationListener.next().getPayloadString(), "perse perse perse perse");
    }

    @Test
    public void blockObservation_multipleBlocks() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, true).payload(" ----- 16 ------").build());

        transport.when(newCoapPacket(3).get().uriPath("/path1").block2Res(2, BlockSize.S_16, false).build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(101).con(Code.C205_CONTENT).obs(2).token(1).payload("------ 16 ------").block2Res(0, BlockSize.S_16, true).build());

        assertEquals(observationListener.next().getPayloadString(), "------ 16 ------ ----- 16 ------e perse");
    }

    @Test
    public void blockObservation_singleBlock() throws Exception {
        //send observation with single last block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(101).con(Code.C205_CONTENT).obs(2).token(1).payload("perse perse").block2Res(0, BlockSize.S_16, false).build());

        assertEquals(observationListener.next().getPayloadString(), "perse perse");
    }

    @Test
    public void blockObservation_noEtagDuplicateFirstBlock() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());
        transport.when(newCoapPacket(3).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("dupa").build());

        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(10).con(Code.C205_CONTENT).obs(2).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());


        assertTrue(observationListener.isEmpty());
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

        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(10).con(Code.C205_CONTENT).obs(2).token(1).block2Res(0, BlockSize.S_16, true).payload("123456789012345|").build());

        assertTrue(observationListener.isEmpty());
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

        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(10).con(Code.C205_CONTENT).obs(2).token(1).block2Res(0, BlockSize.S_16, true).payload("|block_0000_____").build());
        // if default executor is used (asynchronous) - uncomment this line
        //        Thread.sleep(10000);
        assertEquals(observationListener.next().getPayloadString(), expectedPayload.toString());
    }

    @Test
    public void blockObservation_withEtag() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).etag(12).block2Res(1, BlockSize.S_16, false).payload("e perse").build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT).obs(2).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build());

        assertEquals(observationListener.next().getPayloadString(), "perse perse perse perse");
    }

    @Test
    public void blockObservation_etagChanges() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).etag(13).block2Res(1, BlockSize.S_16, false).payload("dupa dupa").build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT).obs(2).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build());

        assertTrue(observationListener.isEmpty());
    }

    @Test
    public void blockObservation_errorResponse() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C400_BAD_REQUEST).build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT).obs(2).token(1).etag(12).payload("perse perse pers").block2Res(0, BlockSize.S_16, true).build());

        assertTrue(observationListener.isEmpty());
    }

    @Test
    public void blockObservation_wrongPayloadSize_firstBlock() throws Exception {
        //send observation with too short block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT)
                .obs(2).token(1).payload("------ 15 ----x").block2Res(0, BlockSize.S_16, true).build());

        assertTrue(observationListener.isEmpty());

        //send observation with too long block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(4).con(Code.C205_CONTENT)
                .obs(2).token(1).payload("------ 17 ------e").block2Res(0, BlockSize.S_16, true).build());

        assertTrue(observationListener.isEmpty());
    }

    @Test
    public void blockObservation_wrongPayloadSize_secondBlock() throws Exception {
        transport.when(newCoapPacket(2).get().uriPath("/path1").block2Res(1, BlockSize.S_16, false).build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).contFormat(MediaTypes.CT_TEXT_PLAIN).block2Res(1, BlockSize.S_16, true).payload("------ 15 ----x").build());

        //send observation with block
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT).contFormat(MediaTypes.CT_TEXT_PLAIN)
                .obs(2).token(1).payload("------ 16 ------").block2Res(0, BlockSize.S_16, true).build());

        assertTrue(observationListener.isEmpty());
    }

}
