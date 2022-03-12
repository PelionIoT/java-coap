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

import static com.mbed.coap.packet.CoapRequest.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.messaging.CoapTcpCSM;
import com.mbed.coap.server.messaging.CoapTcpCSMStorage;
import com.mbed.coap.server.messaging.CoapTcpCSMStorageImpl;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
import com.mbed.coap.transmission.SingleTimeout;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.TransportConnectorMock;


public class BlockwiseTransferWithTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;

    private String payload = "123456789012345|123456789012345|dupa";
    private CoapTcpCSMStorage capabilitiesStorage = new CoapTcpCSMStorageImpl(new CoapTcpCSM(1024, true));

    @BeforeEach
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0))
                .csmStorage(capabilitiesStorage)
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @AfterEach
    public void tearDown() throws IOException {
        client.close();
    }

    @Test
    public void block1_default_size() throws Exception {

        transport.when(newCoapPacket(1).put().uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|dupa").build())
                .then(newCoapPacket(1).ack(Code.C204_CHANGED).build());


        assertEquals(Code.C204_CHANGED, client.sendSync(put("/path1").payload(payload, MediaTypes.CT_TEXT_PLAIN)).getCode());

    }

    @Test
    public void block1_custom_size_per_remote_ip() throws Exception {
        capabilitiesStorage.put(SERVER_ADDRESS, new CoapTcpCSM(32, true));

        transport.when(newCoapPacket(1).put().block1Req(0, BlockSize.S_32, true).size1(payload.length()).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("123456789012345|123456789012345|").build())
                .then(newCoapPacket(1).ack(Code.C231_CONTINUE).block1Req(0, BlockSize.S_32, true).build());

        transport.when(newCoapPacket(2).put().block1Req(1, BlockSize.S_32, false).uriPath("/path1").contFormat(MediaTypes.CT_TEXT_PLAIN).payload("dupa").build())
                .then(newCoapPacket(2).ack(Code.C204_CHANGED).block1Req(1, BlockSize.S_32, false).build());

        assertEquals(Code.C204_CHANGED, client.sendSync(put("/path1").payload(payload, MediaTypes.CT_TEXT_PLAIN)).getCode());

    }


}
