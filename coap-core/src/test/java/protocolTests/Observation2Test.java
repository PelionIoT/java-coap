/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
import com.mbed.coap.server.SimpleObservationIDGenerator;
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
        transport.receive(newCoapPacket(SERVER_ADDRESS).mid(3).con(Code.C205_CONTENT).obs(2).token(1).payload("perse perse").build());

        //important, no token included in response
        assertEquals(transport.getLastOutgoingMessage(), newCoapPacket(SERVER_ADDRESS).mid(3).ack(null).build());
        verify(observationListener).onObservation(hasPayload("perse perse"));
    }
}
