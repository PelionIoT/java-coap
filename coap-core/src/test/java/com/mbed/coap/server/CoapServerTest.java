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
package com.mbed.coap.server;


import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.ObservationNotEstablishedException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.filter.MaxAllowedPayloadFilter;
import com.mbed.coap.server.internal.CoapMessaging;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import protocolTests.utils.CoapPacketBuilder;

public class CoapServerTest {

    private CoapMessaging msg = mock(CoapMessaging.class);
    private CoapServer server;
    private final Service<CoapRequest, CoapResponse> route = RouterService.builder()
            .post("/some*", req ->
                    completedFuture(CoapResponse.ok("OK"))
            )
            .post("/err", req ->
                    failedFuture(new CoapException("error-007"))
            )
            .post("/err2", new MaxAllowedPayloadFilter(100, "too big").then(req ->
                    failedFuture(new RuntimeException()))
            )
            .build();


    @BeforeEach
    public void setUp() throws Exception {
        reset(msg);

        server = new CoapServer(msg, route).start();
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
        final CoapServer nonStartedServer = new CoapServer(msg, RouterService.NOT_FOUND_SERVICE);

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
        server.coapRequestHandler.handleRequest(
                newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/non-existing-resource").build(), TransportContext.NULL
        );

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C404_NOT_FOUND));

    }

    @Test
    public void shouldResponse_to_resource_that_matches_pattern() throws Exception {
        server.coapRequestHandler.handleRequest(
                newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/some-1234").build(), TransportContext.NULL
        );

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("OK"));
    }

    @Test
    public void sendError_when_exceptionWhileHandlingRequest() throws Exception {
        server.coapRequestHandler.handleRequest(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err").build(), TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C500_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void send413_when_RequestEntityTooLarge_whileHandlingRequest() {
        server.coapRequestHandler.handleRequest(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err2").payload("way too big, way too big, way too big, way too big, way too big, way too big, way too big, way too big, way too big").build(), TransportContext.NULL);

        assertSendResponse(newCoapPacket(LOCAL_1_5683).mid(1).size1(100).ack(Code.C413_REQUEST_ENTITY_TOO_LARGE).payload("too big"));
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

    @Test
    public void shouldSendObservationRequest() {
        server.observe("/test", LOCAL_5683, Opaque.of("aa"), TransportContext.NULL);

        verify(msg).makeRequest(argThat(cp -> cp.headers().getUriPath().equals("/test") && cp.headers().getObserve() != null), any(), eq(TransportContext.NULL));
    }

    @Test
    public void shouldSendObservationRequest_andAddObservationHeader() {
        server.observe(newCoapPacket(LOCAL_5683).get().uriPath("/test").build(), TransportContext.NULL);

        verify(msg).makeRequest(argThat(cp -> cp.headers().getUriPath().equals("/test") && cp.headers().getObserve() != null), any(), eq(TransportContext.NULL));
    }

    @Test
    public void shouldRespondToObservationRequest() throws ExecutionException, InterruptedException {
        CoapPacket resp = newCoapPacket().ack(Code.C205_CONTENT).obs(0).build();
        CompletableFuture<CoapPacket> obsResp = server.observe("/test", LOCAL_5683, Opaque.of("aa"), TransportContext.NULL);

        verifyMakeRequest_andThen().call(resp);

        assertEquals(resp, obsResp.get());
    }

    @Test
    public void shouldRespondToObservationRequest_notObserved() {
        CompletableFuture<CoapPacket> resp = server.observe("/test", LOCAL_5683, Opaque.of("aa"), TransportContext.NULL);

        verifyMakeRequest_andThen().call(newCoapPacket().ack(Code.C205_CONTENT).build());

        assertTrue(
                assertThrows(ExecutionException.class, resp::get).getCause() instanceof ObservationNotEstablishedException
        );

    }

    @Test
    public void shouldRespondToObservationRequest_errorResponse() {
        CompletableFuture<CoapPacket> resp = server.observe("/test", LOCAL_5683, Opaque.of("aa"), TransportContext.NULL);

        verifyMakeRequest_andThen().call(newCoapPacket().ack(Code.C404_NOT_FOUND).build());

        assertTrue(
                assertThrows(ExecutionException.class, resp::get).getCause() instanceof ObservationNotEstablishedException
        );
    }

    @Test
    public void shouldRespondToObservationRequest_exception() {
        CompletableFuture<CoapPacket> resp = server.observe("/test", LOCAL_5683, Opaque.of("aa"), TransportContext.NULL);

        verifyMakeRequest_andThen().callException(new IOException());

        assertTrue(
                assertThrows(ExecutionException.class, resp::get).getCause() instanceof IOException
        );
    }

    @Test()
    public void shouldSendNotification() {
        CoapPacket notif = newCoapPacket(LOCAL_5683).obs(12).token(11).ack(Code.C205_CONTENT).build();
        server.sendNotification(notif, TransportContext.NULL);

        verify(msg).makeRequest(eq(notif), any(), any());
    }

    @Test()
    public void failToSendNotificationWithout_missingHeaders() {

        //observation is missing
        assertThatThrownBy(() ->
                server.sendNotification(newCoapPacket(LOCAL_5683).token(11).ack(Code.C205_CONTENT).build(), TransportContext.NULL)
        ).isInstanceOf(IllegalArgumentException.class);

        //token is missing
        assertThatThrownBy(() ->
                server.sendNotification(newCoapPacket(LOCAL_5683).obs(2).ack(Code.C205_CONTENT).build(), TransportContext.NULL)
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

    private Callback<CoapPacket> verifyMakeRequest_andThen() {
        ArgumentCaptor<Callback<CoapPacket>> callback = ArgumentCaptor.forClass(Callback.class);
        verify(msg).makeRequest(any(), callback.capture(), any());
        return callback.getValue();
    }
}
