/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.MessageIdSupplierImpl;
import com.mbed.coap.server.SimpleObservationIDGenerator;
import com.mbed.coap.transmission.SingleTimeout;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by olesmi01 on 18.4.2016.
 * Block2 header option block transfer size limit tests.
 */
public class Block2TransferMaxSizeTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private static final int MAX_TRANSFER_SIZE = 32;
    private TransportConnectorMock transport;
    private CoapClient client;
    private CoapServer coapServer;

    @BeforeEach
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .observerIdGenerator(new SimpleObservationIDGenerator(0))
                .timeout(new SingleTimeout(500))
                .maxIncomingBlockTransferSize(MAX_TRANSFER_SIZE)
                .build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        System.out.println("tearDown -----------");
    }

    @Test
    public void block2_worksFineBelowLimit() throws Exception {
        transport.when(newCoapPacket(1).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0_3456789012345|").build());
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, false).payload("1_3456789012345|").build());

        assertEquals("0_3456789012345|1_3456789012345|", client.resource("/path1").get().get().getPayloadString());
    }


    @Test()
    public void block2_entityTooLargeTest() throws ExecutionException, InterruptedException {
        transport.when(newCoapPacket(1).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0_3456789012345|").build());
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(1, BlockSize.S_16, true).payload("1_3456789012345|").build());
        transport.when(newCoapPacket(3).get().block2Res(2, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(3).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, false).payload("2_3456789012345|").build());

        assertThatThrownBy(() -> client.resource("/path1").get().get().getPayloadString())
                .hasCause(new CoapBlockTooLargeEntityException("Received too large entity for request, max allowed 32, received 48"));
    }

    @Test
    public void block2_incorrectBlockNumber() {
        transport.when(newCoapPacket(1).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(Code.C205_CONTENT).block2Res(0, BlockSize.S_16, true).payload("0_3456789012345|").build());
        transport.when(newCoapPacket(2).get().block2Res(1, BlockSize.S_16, false).uriPath("/path1").build())
                .then(newCoapPacket(2).ack(Code.C205_CONTENT).block2Res(2, BlockSize.S_16, true).payload("1_3456789012345|").build());

        assertThatThrownBy(() -> client.resource("/path1").get().get().getPayloadString())
                .hasCause(new CoapBlockException("Requested and received block number mismatch: req=1|last|16, resp=2|more|16, stopping transaction"));
    }
}
