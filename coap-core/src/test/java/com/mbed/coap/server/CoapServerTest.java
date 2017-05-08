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
package com.mbed.coap.server;


import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CoapServer server;

    @Before
    public void setUp() throws Exception {
        reset(msg);

        server = new CoapServer(msg).start();
    }

    @Test
    public void shouldStartAndStop() throws Exception {
        verify(msg).start(any());
        assertTrue(server.isRunning());

        server.stop();
        verify(msg).stop();
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldFailWhenAttemptToStopWhenNotRunning() throws Exception {
        final CoapServer nonStartedServer = new CoapServer(msg);

        assertThatThrownBy(nonStartedServer::stop).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldPassMakeRequest_toMessaging() throws ExecutionException, InterruptedException {
        final CoapPacket req = newCoapPacket().get().uriPath("/test").build();
        final ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);

        //when
        final CompletableFuture<CoapPacket> resp = server.makeRequest(req);

        //then
        verify(msg).makeRequest(eq(req), callback.capture(), eq(TransportContext.NULL));
        assertFalse(resp.isDone());

        //verify callback
        callback.getValue().call(newCoapPacket().ack(Code.C400_BAD_REQUEST).build());
        assertTrue(resp.isDone());
        assertEquals(Code.C400_BAD_REQUEST, resp.get().getCode());
    }

    @Test
    public void shouldResponseWith404_to_unknownResource() throws Exception {
        server.addRequestHandler("/some*", new ReadOnlyCoapResource("1"));
        server.addRequestHandler("/3", new ReadOnlyCoapResource("3"));

        server.coapRequestHandler.handleRequest(
                newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/non-existing-resource").build(), TransportContext.NULL
        );

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C404_NOT_FOUND));

    }

    @Test
    public void shouldResponse_to_resource_that_matches_pattern() throws Exception {
        server.addRequestHandler("/some*", exchange -> {
            exchange.setResponseBody("OK");
            exchange.sendResponse();
        });

        server.coapRequestHandler.handleRequest(
                newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/some-1234").build(), TransportContext.NULL
        );

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("OK"));
    }

    @Test
    public void sendError_when_exceptionWhileHandlingRequest() throws Exception {
        server.addRequestHandler("/err", exchange -> {
            throw new CoapException("error-007");
        });

        server.coapRequestHandler.handleRequest(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err").build(), TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C500_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void send413_when_RequestEntityTooLarge_whileHandlingRequest() {
        server.addRequestHandler("/err", exchange -> {
            throw new CoapRequestEntityTooLarge(100, "too big");
        });

        server.coapRequestHandler.handleRequest(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err").build(), TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).size1(100).ack(Code.C413_REQUEST_ENTITY_TOO_LARGE).payload("too big"));
    }

    @Test
    public void send413_when_RequestEntityTooLarge_with_block_whileHandlingRequest() {
        server.addRequestHandler("/err", exchange -> {
            throw new CoapRequestEntityTooLarge(new BlockOption(0, BlockSize.S_64, true), "too big");
        });

        server.coapRequestHandler.handleRequest(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err").build(), TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).block1Req(0, BlockSize.S_64, true).ack(Code.C413_REQUEST_ENTITY_TOO_LARGE).payload("too big"));
    }


    @Test
    public void sendError_when_CoapCodeException_whileHandlingRequest() throws Exception {
        server.addRequestHandler("/err", exchange -> {
            throw new CoapCodeException(Code.C503_SERVICE_UNAVAILABLE, new IOException());
        });

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/err").build();
        server.coapRequestHandler.handleRequest(req, TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C503_SERVICE_UNAVAILABLE).payload("503 SERVICE UNAVAILABLE"));
    }

    @Test
    public void receiveObservationCancellation_withCode() throws Exception {
        ObservationHandler observationHandler = mock(ObservationHandler.class);
        server.setObservationHandler(observationHandler);

        server.coapRequestHandler.handleObservation(newCoapPacket(LOCAL_5683).con(Code.C404_NOT_FOUND).obs(0).token(33).payload("A").mid(2).build(), TransportContext.NULL);

        verify(observationHandler).callException(isA(ObservationTerminatedException.class));

        //ack response
        assertSendResponse(newCoapPacket(LOCAL_5683).emptyAck(2));
    }

    //    ?????
    //    @Test
    //    public void receiveObservationCancellation_withCode_noObsHeader() throws Exception {
    //        ObservationHandler observationHandler = initServerWithObservationHandler();
    //
    //        server.coapRequestHandler.handleObservation(newCoapPacket(LOCAL_5683).con(Code.C404_NOT_FOUND).token(33).payload("A").mid(3).build(), TransportContext.NULL);
    //        verify(observationHandler, never()).callException(any());
    //
    //        //ack response
    //        assertSendResponse(newCoapPacket(LOCAL_5683).reset().mid(3));
    //    }


    @Test
    public void shouldSendObservationRequest() {
        Callback<CoapPacket> callback = mock(Callback.class);
        server.observe("/test", LOCAL_5683, callback, "aa".getBytes(), TransportContext.NULL);

        verify(msg).makeRequest(argThat(cp -> cp.headers().getUriPath().equals("/test") && cp.headers().getObserve() != null), any(), eq(TransportContext.NULL));
    }

    @Test
    public void shouldSendObservationRequest_andAddObservationHeader() {
        Callback<CoapPacket> callback = mock(Callback.class);
        server.observe(newCoapPacket(LOCAL_5683).get().uriPath("/test").build(), callback, TransportContext.NULL);

        verify(msg).makeRequest(argThat(cp -> cp.headers().getUriPath().equals("/test") && cp.headers().getObserve() != null), any(), eq(TransportContext.NULL));
    }

    @Test
    public void shouldRespondToObservationRequest() {
        CoapPacket resp = newCoapPacket().ack(Code.C205_CONTENT).obs(0).build();
        RequestCallback respCallback = mock(RequestCallback.class);
        server.observe("/test", LOCAL_5683, respCallback, "aa".getBytes(), TransportContext.NULL);

        verifyMakeRequest_andThen().onSent();
        verifyMakeRequest_andThen().call(resp);

        verify(respCallback).onSent();
        verify(respCallback).call(eq(resp));
    }

    @Test
    public void shouldRespondToObservationRequest_notObserved() {
        Callback<CoapPacket> respCallback = mock(Callback.class);
        server.observe("/test", LOCAL_5683, respCallback, "aa".getBytes(), TransportContext.NULL);

        verifyMakeRequest_andThen().onSent();
        verifyMakeRequest_andThen().call(newCoapPacket().ack(Code.C205_CONTENT).build());

        verify(respCallback).callException(isA(ObservationNotEstablishedException.class));
    }

    @Test
    public void shouldRespondToObservationRequest_errorResponse() {
        Callback<CoapPacket> respCallback = mock(Callback.class);
        server.observe("/test", LOCAL_5683, respCallback, "aa".getBytes(), TransportContext.NULL);

        verifyMakeRequest_andThen().call(newCoapPacket().ack(Code.C404_NOT_FOUND).build());

        verify(respCallback).callException(isA(ObservationNotEstablishedException.class));
    }

    @Test
    public void shouldRespondToObservationRequest_exception() {
        Callback<CoapPacket> respCallback = mock(Callback.class);
        server.observe("/test", LOCAL_5683, respCallback, "aa".getBytes(), TransportContext.NULL);

        verifyMakeRequest_andThen().callException(new IOException());

        verify(respCallback).callException(isA(IOException.class));
    }

    @Test()
    public void shouldSendNotification() {
        CoapPacket notif = newCoapPacket(LOCAL_5683).obs(12).token(11).ack(Code.C205_CONTENT).build();
        server.sendNotification(notif, mock(Callback.class), TransportContext.NULL);

        verify(msg).makeRequest(eq(notif), any(), any());
    }

    @Test()
    public void failToSendNotificationWithout_missingHeaders() {

        //observation is missing
        assertThatThrownBy(() ->
                server.sendNotification(newCoapPacket(LOCAL_5683).token(11).ack(Code.C205_CONTENT).build(), mock(Callback.class), TransportContext.NULL)
        ).isInstanceOf(IllegalArgumentException.class);

        //token is missing
        assertThatThrownBy(() ->
                server.sendNotification(newCoapPacket(LOCAL_5683).obs(2).ack(Code.C205_CONTENT).build(), mock(Callback.class), TransportContext.NULL)
        ).isInstanceOf(IllegalArgumentException.class);

        //wrong content type
        assertThatThrownBy(() ->
                server.sendNotification(newCoapPacket(LOCAL_5683).token(321).obs(2).ack(Code.C400_BAD_REQUEST).build(), mock(Callback.class), TransportContext.NULL)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void should_pass_disconnectionHandler() {
        Consumer<InetSocketAddress> disconnectConsumer = inetSocketAddress -> {
        };

        //when
        server.setConnectHandler(disconnectConsumer);

        //then
        verify(msg).setConnectHandler(eq(disconnectConsumer));
    }

    //---------------

    private void assertSendResponse(CoapPacketBuilder resp) {
        assertSendResponse(resp.build());
    }

    private void assertSendResponse(CoapPacket resp) {
        verify(msg).sendResponse(any(), eq(resp), any());
    }

    private ObservationHandler initServerWithObservationHandler() {
        ObservationHandler observationHandler = mock(ObservationHandler.class);
        when(observationHandler.hasObservation(any())).thenReturn(true);
        server.setObservationHandler(observationHandler);
        return observationHandler;
    }

    private RequestCallback verifyMakeRequest_andThen() {
        ArgumentCaptor<RequestCallback> callback = ArgumentCaptor.forClass(RequestCallback.class);
        verify(msg).makeRequest(any(), callback.capture(), any());
        return callback.getValue();
    }
}
