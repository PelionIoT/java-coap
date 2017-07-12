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
package com.mbed.coap.server.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.exception.ObservationTerminatedException;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.server.ObservationHandler;
import com.mbed.coap.server.internal.CoapTransaction.Priority;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.ReadOnlyCoapResource;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;

/**
 * Created by szymon
 */
public class CoapServerForUdpTest {


    private final CoapTransport coapTransport = mock(CoapTransport.class);
    private int mid = 100;
    private final MessageIdSupplier midSupplier = () -> mid++;
    private CoapServerForUdp server;
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    private ObservationHandler observationHandler;
    private BlockSize blockSize = null;


    @Before
    public void setUp() throws Exception {
        server = new CoapServerForUdp() {
            @Override
            public byte[] observe(String uri, InetSocketAddress destination, Callback<CoapPacket> respCallback, byte[] token, TransportContext transportContext) {
                return new byte[0];
            }

            @Override
            public byte[] observe(CoapPacket request, Callback<CoapPacket> respCallback, TransportContext transportContext) {
                return new byte[0];
            }
        };

        resetCoapTransport();
    }

    private void resetCoapTransport() {
        reset(coapTransport);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));
    }

    private void initWithObservationHandler() {
        initServer();

        observationHandler = mock(ObservationHandler.class);
        when(observationHandler.hasObservation(any())).thenReturn(true);
        server.setObservationHandler(observationHandler);
    }

    @Test
    public void failWhenNoTransportIsProvided() throws Exception {
        assertFalse(server.isRunning());

        assertThatThrownBy(() -> server.init(1, null, null, false, null, 1, null, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> server.init(1, coapTransport, null, false, null, 1, null, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> server.init(1, coapTransport, scheduledExecutor, false, null, 1, null, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> server.init(1, coapTransport, scheduledExecutor, false, () -> 1, 1, null, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    public void doNotStopScheduleExecutor() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, mock(MessageIdSupplier.class), 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);
        server.start();
        assertEquals(scheduledExecutor, server.getScheduledExecutor());

        server.stop();
        verify(scheduledExecutor, never()).shutdown();
    }

    @Test
    public void shouldFailToMakeRequest_whenQueueIsFull() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        CompletableFuture<CoapPacket> resp1 = server.makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/10").build());
        assertFalse(resp1.isDone());

        //should fail to make a request
        CompletableFuture<CoapPacket> resp2 = server.makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/11").build());

        assertTrue(resp2.isDone());
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(TooManyRequestsForEndpointException.class);
    }

    @Test
    public void failToMakeRequestWhenMissingParameters() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        //missing address
        assertThatThrownBy(() ->
                server.makeRequest(newCoapPacket(123).get().uriPath("/10").build(), mock(Callback.class))
        ).isExactlyInstanceOf(NullPointerException.class);

        //missing coap packet
        assertThatThrownBy(() ->
                server.makeRequest(null, mock(Callback.class))
        ).isExactlyInstanceOf(NullPointerException.class);

        //missing callback
        assertThatThrownBy(() ->
                server.makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/10").build(), ((Callback) null))
        ).isExactlyInstanceOf(NullPointerException.class);

    }

    @Test
    public void responseToPingMessage() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).con(null));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    public void ignore_nonProcessedMessage() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C203_VALID));
        verify(coapTransport, never()).sendPacket(any(), any(), any());

        receive(newCoapPacket(LOCAL_1_5683).reset(1));
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void duplicateRequest() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        AtomicInteger counter = new AtomicInteger(0);
        server.addRequestHandler("/19", exchange -> {
            exchange.setResponseBody("ABC" + counter.getAndIncrement());
            exchange.sendResponse();
        });


        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
        resetCoapTransport();

        //duplicate
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
    }

    @Test
    public void duplicateRequest_noDuplicateDetector() throws Exception {
        server.init(0, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        AtomicInteger counter = new AtomicInteger(0);
        server.addRequestHandler("/19", exchange -> {
            exchange.setResponseBody("ABC" + counter.getAndIncrement());
            exchange.sendResponse();
        });


        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
        resetCoapTransport();

        //duplicate
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC1"));
    }


    @Test
    public void sendError_when_exceptionWhileHandlingRequest() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        server.addRequestHandler("/err", exchange -> {
            throw new CoapException("error-007");
        });

        receive(newCoapPacket(LOCAL_1_5683).mid(1).con().post().uriPath("/err"));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C500_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void sendError_when_CoapCodeException_whileHandlingRequest() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        server.addRequestHandler("/err", exchange -> {
            throw new CoapCodeException(Code.C503_SERVICE_UNAVAILABLE, new IOException());
        });

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/err").build();
        receive(req);

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C503_SERVICE_UNAVAILABLE).payload("503 SERVICE UNAVAILABLE"));
        resetCoapTransport();
    }

    @Test
    public void non_request_response() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        //request
        CompletableFuture<CoapPacket> resp = server.makeRequest(newCoapPacket(LOCAL_5683).mid(1).token(1001).non().get().build());

        //response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).non(Code.C203_VALID));

        assertEquals(Code.C203_VALID, resp.get().getCode());
    }

    @Test
    public void separate_confirmable_response() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        //request
        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).mid(100).token(1001).con().get();
        CompletableFuture<CoapPacket> resp = server.makeRequest(req.build());
        assertSent(req);

        //response - empty ack
        receive(newCoapPacket(LOCAL_5683).emptyAck(100));

        //separate confirmable response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(null));

        assertEquals(Code.C205_CONTENT, resp.get().getCode());
    }


    @Test
    public void networkError_whileHandlingRequest() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);

        server.addRequestHandler("/19", new ReadOnlyCoapResource("ABC"));

        //IOException
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        receive(newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/19"));
        verify(coapTransport, only()).sendPacket(any(), any(), any());

        //IOException
        resetCoapTransport();
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        receive(newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/19"));
        verify(coapTransport, only()).sendPacket(any(), any(), any());

    }

    @Test
    public void sendRetransmissions() throws Exception {
        initServer();
        server.setTransmissionTimeout(new TestTransmissionTimeout(2));

        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).get().uriPath("/10");
        CompletableFuture<CoapPacket> resp = server.makeRequest(req.build());

        Thread.sleep(1);
        server.resendTimeouts();
        Thread.sleep(1);
        server.resendTimeouts();

        verify(coapTransport, times(2)).sendPacket(eq(req.build()), any(), any());

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
    }

    @Test
    public void networkFail_whenRetransmissions() throws Exception {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);
        server.setTransmissionTimeout(new CoapTimeout(1, 5));

        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).get().uriPath("/10");
        CompletableFuture<CoapPacket> resp = server.makeRequest(req.build());

        Thread.sleep(1);

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        server.resendTimeouts();

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void shouldFail_toMakeSecondRequestFromQueue() throws Exception {
        initServer();

        CompletableFuture<CoapPacket> resp1 = server.makeRequest(newCoapPacket(LOCAL_5683).mid(100).get().uriPath("/10").build());
        CompletableFuture<CoapPacket> resp2 = server.makeRequest(newCoapPacket(LOCAL_5683).mid(101).get().uriPath("/11").build());

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        receive(newCoapPacket(LOCAL_5683).mid(100).ack(Code.C205_CONTENT).payload("ABC"));

        assertEquals("ABC", resp1.get().getPayloadString());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void should_receive_onSent_callback_when_message_is_sent() throws Exception {
        initServer();

        RequestCallback callback = mock(RequestCallback.class);
        CompletableFuture<Boolean> fut = new CompletableFuture<>();
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(fut);

        server.makeRequest(newCoapPacket(LOCAL_1_5683).con().get().uriPath("/test").build(), callback);

        verify(callback, never()).onSent();

        fut.complete(true);
        verify(callback).onSent();
    }

    @Test
    public void should_receive_onSent_callback_when_NON_message_is_sent() throws Exception {
        initServer();

        RequestCallback callback = mock(RequestCallback.class);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(true));

        server.makeRequest(newCoapPacket(LOCAL_1_5683).non().get().uriPath("/test").build(), callback);

        verify(callback).onSent();
    }

    @Test
    public void network_fail_when_sending_NON_request() throws Exception {
        initServer();

        RequestCallback callback = mock(RequestCallback.class);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());

        server.makeRequest(newCoapPacket(LOCAL_1_5683).non().token(12).get().uriPath("/test").build(), callback);

        verify(callback, never()).onSent();
        verify(callback).callException(any());
    }

    // --- OBSERVATIONS ---

    @Test
    public void receiveObservation() throws Exception {
        initWithObservationHandler();

        receive(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));
        receive(newCoapPacket(LOCAL_5683).mid(3002).obs(2).con(Code.C205_CONTENT).token(44).payload("B"));

        verify(observationHandler, times(2)).call(any());

        //no error was send
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void receiveObservationCancellation_withCode() throws Exception {
        initWithObservationHandler();

        receive(newCoapPacket(LOCAL_5683).con(Code.C404_NOT_FOUND).token(33).payload("A"));
        verify(observationHandler).callException(isA(ObservationTerminatedException.class));

        //ack response
        assertSent(newCoapPacket(LOCAL_5683).emptyAck(0));
    }

    @Test
    public void receiveObservationCancellation_withDeregisterObs() throws Exception {
        initWithObservationHandler();

        receive(newCoapPacket(LOCAL_5683).obs(1).con(Code.C203_VALID).token(33));
        verify(observationHandler).callException(isA(ObservationTerminatedException.class));

        //ack response
        assertSent(newCoapPacket(LOCAL_5683).emptyAck(0));
    }

    @Test
    public void receiveObservationCancellation_withDeregisterObs_non() throws Exception {
        initWithObservationHandler();

        receive(newCoapPacket(LOCAL_5683).obs(1).non(Code.C203_VALID).token(33));
        verify(observationHandler).callException(isA(ObservationTerminatedException.class));

        //no ack
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void should_receive_callback_when_observation_request_is_sent() throws Exception {
        server = new CoapServerBlocks();
        initServer();

        RequestCallback callback = mock(RequestCallback.class);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(true));

        server.observe("/test1", LOCAL_1_5683, callback, "12".getBytes(), TransportContext.NULL);

        verify(callback).onSent();
    }

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        server.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void receive(CoapPacket coapPacket) {
        server.handle(coapPacket, TransportContext.NULL);
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacket), any(), any());
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

    private CompletableFuture<Boolean> exceptionFuture() {
        CompletableFuture completableFuture = new CompletableFuture();
        completableFuture.completeExceptionally(new IOException("no connection"));
        return completableFuture;
    }

    private void initServer() {
        server.init(10, coapTransport, scheduledExecutor, false, midSupplier, 10, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);
    }

    private static class TestTransmissionTimeout implements TransmissionTimeout {

        private final int maxRetry;

        private TestTransmissionTimeout(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        @Override
        public long getTimeout(int attemptCounter) {
            if (attemptCounter > maxRetry) {
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        public long getMulticastTimeout(int attempt) {
            return 0;
        }
    }
}