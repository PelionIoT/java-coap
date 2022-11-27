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

import static com.mbed.coap.packet.CoapRequest.put;
import static com.mbed.coap.transmission.RetransmissionBackOff.ofFixed;
import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static protocolTests.utils.CoapPacketBuilder.newCoapPacket;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MediaTypes;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.messaging.MessageIdSupplierImpl;
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

    @BeforeEach
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        client = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0))
                .retransmission(ofFixed(ofMillis(500)))
                .buildClient(SERVER_ADDRESS);
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

}
