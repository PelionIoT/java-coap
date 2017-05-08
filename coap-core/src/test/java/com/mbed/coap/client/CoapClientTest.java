/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.client;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.FutureCallbackAdapter;
import com.mbed.coap.utils.Token;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java8.util.concurrent.CompletableFuture;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import protocolTests.utils.CoapPacketBuilder;

/**
 * Created by szymon
 */
public class CoapClientTest {
    private final BlockingCoapTransport coapTransport = mock(BlockingCoapTransport.class);
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class, Mockito.RETURNS_DEEP_STUBS);
    private int mid = 100;
    private final MessageIdSupplier midSupplier = () -> mid++;
    private CoapClient client;

    @Before
    public void setUp() throws Exception {
        client = CoapClientBuilder.newBuilder().transport(coapTransport).target(LOCAL_5683).scheduledExec(scheduledExecutor).midSupplier(midSupplier).build();
    }

    @Test
    public void request() throws Exception {
        CompletableFuture<CoapPacket> resp = client.resource("/test").get();
        assertSent(newCoapPacket(LOCAL_5683).mid(100).uriPath("/test").get());

        cliReceive(newCoapPacket(LOCAL_5683).mid(100).payload("ABC").ack(Code.C205_CONTENT));
        assertEquals("ABC", resp.get().getPayloadString());

    }

    @Test
    public void pingRequest() throws Exception {
        CompletableFuture<CoapPacket> resp = client.ping();
        assertSent(newCoapPacket(LOCAL_5683).mid(100).con());

        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(100));
        assertNotNull(resp.get());
    }

    @Test
    public void pingRequest2() throws Exception {
        FutureCallbackAdapter<CoapPacket> resp = new FutureCallbackAdapter<>();
        client.ping(resp);

        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(100));
        assertNotNull(resp.get());
    }

    @Test
    public void methods_inRequest() throws Exception {
        client.resource("/test").get(Callback.IGNORE);
        assertSent(newCoapPacket(LOCAL_5683).mid(100).uriPath("/test").get());
        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(100));

        client.resource("/test").put(Callback.IGNORE);
        assertSent(newCoapPacket(LOCAL_5683).mid(101).uriPath("/test").put());
        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(101));

        client.resource("/test").delete(Callback.IGNORE);
        assertSent(newCoapPacket(LOCAL_5683).mid(102).uriPath("/test").delete());
        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(102));

        client.resource("/test").post(Callback.IGNORE);
        assertSent(newCoapPacket(LOCAL_5683).mid(103).uriPath("/test").post());
        cliReceive(newCoapPacket(LOCAL_5683).emptyAck(103));
    }

    @Test
    public void syncRequest() throws Exception {
        doAnswer(m -> {
            cliReceive(newCoapPacket(LOCAL_5683).mid(100).payload("AAA").ack(Code.C205_CONTENT).build());
            return null;
        }).when(coapTransport).sendPacket0(eq(newCoapPacket(LOCAL_5683).mid(100).uriPath("/test").get().build()), any(), any());

        assertNotNull(client.resource("/test").sync().invokeMethod(Method.GET));

        //PUT
        doAnswer(m -> {
            cliReceive(newCoapPacket(LOCAL_5683).mid(101).payload("AAA").ack(Code.C205_CONTENT).build());
            return null;
        }).when(coapTransport).sendPacket0(eq(newCoapPacket(LOCAL_5683).mid(101).uriPath("/test").put().build()), any(), any());

        assertNotNull(client.resource("/test").sync().invokeMethod(Method.PUT));

        //POST
        doAnswer(m -> {
            cliReceive(newCoapPacket(LOCAL_5683).mid(102).payload("AAA").ack(Code.C205_CONTENT).build());
            return null;
        }).when(coapTransport).sendPacket0(eq(newCoapPacket(LOCAL_5683).mid(102).uriPath("/test").post().build()), any(), any());

        assertNotNull(client.resource("/test").sync().invokeMethod(Method.POST));

        //DELETE
        doAnswer(m -> {
            cliReceive(newCoapPacket(LOCAL_5683).mid(103).payload("AAA").ack(Code.C205_CONTENT).build());
            return null;
        }).when(coapTransport).sendPacket0(eq(newCoapPacket(LOCAL_5683).mid(103).uriPath("/test").delete().build()), any(), any());

        assertNotNull(client.resource("/test").sync().invokeMethod(Method.DELETE));
    }


    @Test
    public void failWhenSyncRequest() throws Exception {
        doThrow(new CoapException("")).when(coapTransport).sendPacket(any(), any(), any());
        assertThatThrownBy(() -> client.resource("/test").sync().get()).isExactlyInstanceOf(CoapException.class);

        doThrow(new IOException(new CoapException(""))).when(coapTransport).sendPacket(any(), any(), any());
        assertThatThrownBy(() -> client.resource("/test").sync().post()).isExactlyInstanceOf(CoapException.class);

        doThrow(new IOException()).when(coapTransport).sendPacket(any(), any(), any());
        assertThatThrownBy(() -> client.resource("/test").sync().put()).hasCauseExactlyInstanceOf(IOException.class);

        doThrow(new CoapException("")).when(coapTransport).sendPacket(any(), any(), any());
        assertThatThrownBy(() -> client.resource("/test").sync().delete()).isExactlyInstanceOf(CoapException.class);
    }


    @Test(expected = IllegalStateException.class)
    public void failToCreate_whenNotStartedServer() throws Exception {
        new CoapClient(LOCAL_5683, CoapServerBuilder.newBuilder().transport(coapTransport).build());
    }

    @Test(expected = IllegalStateException.class)
    public void failToMakeRequest_whenServerNotRunning() throws Exception {
        client.coapServer.stop();
        client.resource("/123");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failToMakeRequest_whenIllegalCharacterInPath() throws Exception {
        client.resource("fs?fs");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failToMakeRequest_whenIllegalCharacterInPath2() throws Exception {
        client.resource("fs&fs");
    }

    @Test
    public void observationTest() throws Exception {
        ObservationListener obsListener = mock(ObservationListener.class);
        CompletableFuture<CoapPacket> resp = client.resource("/test").token(1001).observe(obsListener);

        assertSent(newCoapPacket(LOCAL_5683).mid(100).token(1001).obs(0).uriPath("/test").get());
        cliReceive(newCoapPacket(LOCAL_5683).mid(100).token(1001).obs(1).payload("1").ack(Code.C205_CONTENT));
        assertEquals("1", resp.get().getPayloadString());

        //observation
        cliReceive(newCoapPacket(LOCAL_5683).mid(200).token(1001).obs(2).payload("2").con(Code.C205_CONTENT));
        verify(obsListener).onObservation(newCoapPacket(LOCAL_5683).mid(200).token(1001).obs(2).payload("2").con(Code.C205_CONTENT).build());

    }

    @Test
    public void observation_notExpectedObs() throws Exception {
        ObservationListener obsListener = mock(ObservationListener.class);

        CompletableFuture<CoapPacket> resp = client.resource("/test").token(1001).observe(obsListener);
        cliReceive(newCoapPacket(LOCAL_5683).mid(100).token(1001).obs(1).payload("1").ack(Code.C205_CONTENT));
        assertEquals("1", resp.get().getPayloadString());

        //observation
        cliReceive(newCoapPacket(LOCAL_5683).mid(200).token(999).obs(2).payload("2").con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).reset(200));

        verify(obsListener, never()).onObservation(any());
    }

    @Test
    public void observation_failWhileHandling() throws Exception {
        ObservationListener obsListener = mock(ObservationListener.class);
        CompletableFuture<CoapPacket> resp = client.resource("/test").token(1001).observe(obsListener);

        cliReceive(newCoapPacket(LOCAL_5683).mid(100).token(1001).obs(1).payload("1").ack(Code.C205_CONTENT));
        assertEquals("1", resp.get().getPayloadString());

        //observation
        reset(coapTransport);
        doThrow(new CoapCodeException(Code.C400_BAD_REQUEST))
                .when(obsListener).onObservation(any());

        cliReceive(newCoapPacket(LOCAL_5683).mid(200).token(1001).obs(2).payload("2").con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).mid(200).ack(Code.C400_BAD_REQUEST));

    }


    @Test
    public void observation_failWhileHandling2() throws Exception {
        ObservationListener obsListener = mock(ObservationListener.class);
        CompletableFuture<CoapPacket> resp = client.resource("/test").token(1001).observe(obsListener);

        cliReceive(newCoapPacket(LOCAL_5683).mid(100).token(1001).obs(1).payload("1").ack(Code.C205_CONTENT));
        assertEquals("1", resp.get().getPayloadString());

        //observation
        reset(coapTransport);
        doThrow(new CoapException(""))
                .when(obsListener).onObservation(any());

        cliReceive(newCoapPacket(LOCAL_5683).mid(200).token(1001).obs(2).payload("2").con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).mid(200).ack(Code.C500_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void requestWithProxyUri() throws Exception {
        CompletableFuture<CoapPacket> resp = client.resource("/test").proxy("/external").get();
        assertSent(newCoapPacket(LOCAL_5683).mid(100).uriPath("/test").proxy("/external").get());

        cliReceive(newCoapPacket(LOCAL_5683).mid(100).payload("EXT-ABC").ack(Code.C205_CONTENT));
        assertEquals("EXT-ABC", resp.get().getPayloadString());
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(Token.class).suppress(Warning.NONFINAL_FIELDS).usingGetClass().verify();
    }


    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(coapTransport).sendPacket0(eq(coapPacket), any(), any());
    }

    private void cliReceive(CoapPacketBuilder coapPacketBuilder) {
        cliReceive(coapPacketBuilder.build());
    }

    private void cliReceive(CoapPacket packet) {
        client.coapServer.getCoapMessaging().handle(packet, TransportContext.NULL);
    }
}