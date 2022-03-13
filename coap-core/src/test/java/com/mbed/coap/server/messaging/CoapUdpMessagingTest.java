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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.*;
import static com.mbed.coap.packet.Opaque.*;
import static com.mbed.coap.transport.TransportContext.*;
import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.PutOnlyMap;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;


public class CoapUdpMessagingTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    private Function<SeparateResponse, Boolean> observationHandler = mock(Function.class);
    private int mid = 100;
    private final MessageIdSupplier midSupplier = () -> mid++;
    private CoapUdpMessaging udpMessaging;
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    private int counter = 0;
    private Service<CoapRequest, CoapResponse> requestService = req -> completedFuture(ok("ABC" + counter++));


    @BeforeEach
    public void setUp() throws Exception {
        reset(observationHandler);
        given(observationHandler.apply(any())).willReturn(true);
        counter = 0;

        udpMessaging = new CoapUdpMessaging(coapTransport);

        resetCoapTransport();
    }

    private void resetCoapTransport() {
        reset(coapTransport);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(completedFuture(null));
    }

    @Test
    public void failWhenNoTransportIsProvided() throws Exception {
        assertThatThrownBy(() -> udpMessagingInit(1, null, false, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> udpMessagingInit(1, scheduledExecutor, false, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    public void doNotStopScheduleExecutor() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, mock(MessageIdSupplier.class), 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);
        assertEquals(scheduledExecutor, udpMessaging.getScheduledExecutor());

        udpMessaging.stop();
        verify(scheduledExecutor, never()).shutdown();
    }

    @Test
    public void shouldSucceedToMakeRequest_whenQueueIsFull_forceAdd() throws Exception {
        //init with max queue size of 1
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        CompletableFuture<CoapResponse> resp1 = udpMessaging.send(get(LOCAL_5683, "/10"));
        assertFalse(resp1.isDone());

        //should not fail to make a request
        CompletableFuture<CoapResponse> resp2 = udpMessaging.send(get(LOCAL_5683, "/11").block1Req(1, BlockSize.S_16, true));

        assertFalse(resp2.isDone());
        assertFalse(resp2.isCompletedExceptionally());
    }

    @Test
    public void failToMakeRequestWhenMissingParameters() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        //missing address
        assertThatThrownBy(() ->
                udpMessaging.send(get("/10")).join()
        ).isExactlyInstanceOf(NullPointerException.class);

        //missing coap packet
        assertThatThrownBy(() ->
                udpMessaging.send(((CoapRequest) null))
        ).isExactlyInstanceOf(NullPointerException.class);

    }

    @Test
    public void responseToPingMessage() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).con(null));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    void sendResetToUnexpectedMessage() throws Exception {
        initServer();

        receive(newCoapPacket(LOCAL_1_5683).mid(1).con(Code.C205_CONTENT).payload("dupa"));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    public void ignore_nonProcessedMessage() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C203_VALID));
        verify(coapTransport, never()).sendPacket(any(), any(), any());

        receive(newCoapPacket(LOCAL_1_5683).reset(1));
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void duplicateRequest() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
        resetCoapTransport();

        //duplicate
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
    }

    @Test
    public void duplicateResponseToGetRequest() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);
        CompletableFuture<CoapResponse> resp1 = udpMessaging.send(get(LOCAL_5683, "/10"));
        assertFalse(resp1.isDone());
        CoapPacket respPacket = newCoapPacket(LOCAL_5683).mid(100).obs(0).ack(Code.C205_CONTENT).token(33).payload("A").build();
        receive(respPacket);
        assertTrue(resp1.isDone());
        receive(respPacket);
        verify(observationHandler, never()).apply(any());
    }

    @Test
    public void duplicateRequest_noDuplicateDetector() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();

        receive(req);
        receive(req);

        CoapPacket expected = newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0").build();
        verify(coapTransport, times(2)).sendPacket(eq(expected), any(), any());
    }

    @Test
    public void non_request_response() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);

        //request
        CompletableFuture<CoapResponse> resp = udpMessaging.send(get(LOCAL_5683, "/").token(1001).context(NON_CONFIRMABLE));
        assertSent(newCoapPacket(LOCAL_5683).mid(100).token(1001).non().get());

        //response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).non(Code.C203_VALID));

        assertEquals(Code.C203_VALID, resp.get().getCode());
    }

    @Test
    public void separate_confirmable_response() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);

        //request
        CompletableFuture<CoapResponse> resp = udpMessaging.send(get(LOCAL_5683, "/").token(1001));
        assertSent(newCoapPacket(LOCAL_5683).mid(100).token(1001).con().get());

        //response - empty ack
        receive(newCoapPacket(LOCAL_5683).emptyAck(100));

        //separate confirmable response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(null));

        assertEquals(CoapResponse.of(Code.C205_CONTENT), resp.get());
    }

    @Test
    public void sendRetransmissions() throws Exception {
        initServer();
        udpMessaging.setTransmissionTimeout(new TestTransmissionTimeout(2));

        CompletableFuture<CoapResponse> resp = udpMessaging.send(get(LOCAL_5683, "/10"));

        Thread.sleep(1);
        udpMessaging.resendTimeouts();
        Thread.sleep(1);
        udpMessaging.resendTimeouts();

        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).mid(100).get().uriPath("/10");
        verify(coapTransport, times(2)).sendPacket(eq(req.build()), any(), any());

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
    }

    @Test
    public void networkFail_whenRetransmissions() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.setTransmissionTimeout(new CoapTimeout(1, 5));

        CompletableFuture<CoapResponse> resp = udpMessaging.send(get(LOCAL_5683, "/10"));

        Thread.sleep(1);

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        udpMessaging.resendTimeouts();

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
    }


    @Test
    public void network_fail_when_sending_NON_request() throws Exception {
        initServer();

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());

        CompletableFuture<CoapResponse> resp = udpMessaging.send(get(LOCAL_1_5683, "/test").context(NON_CONFIRMABLE));

        assertTrue(resp.isCompletedExceptionally());
    }

    // --- OBSERVATIONS ---

    @Test
    public void receiveObservation() throws Exception {
        initServer();

        receive(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));
        receive(newCoapPacket(LOCAL_5683).mid(3002).obs(2).con(Code.C205_CONTENT).token(44).payload("B"));

        verify(observationHandler, times(2)).apply(any());
    }

    @Test
    public void receiveObservationRetransmission() throws Exception {
        initServer();

        // when
        receive(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));
        receive(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));

        // then
        verify(coapTransport, times(2)).sendPacket(eq(newCoapPacket(LOCAL_5683).emptyAck(3001)), any(), any());
        verify(observationHandler, times(1)).apply(any());
    }

    @Test
    public void shouldSetMessageIdOnMakeRequest() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.send(get(LOCAL_5683, "/test"));

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).get().uriPath("/test"));
    }

    @Test
    public void shouldSetMessageIdOnSendNonResponse() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(0).build(), newCoapPacket(LOCAL_5683).mid(0).non(Code.C205_CONTENT).build(), TransportContext.EMPTY);

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).non(Code.C205_CONTENT));
    }

    @Test
    public void shouldSetMessageIdOnSend_resetResponse_toNonRequest() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(0).non().get().build(), newCoapPacket(LOCAL_5683).mid(0).reset().build(), TransportContext.EMPTY);

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).reset());
    }

    @Test
    public void shouldNotSetMessageId_onSendAckResponse() throws IOException, CoapException {
        initServer();

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(2).build(), newCoapPacket(LOCAL_5683).mid(2).ack(Code.C205_CONTENT).build(), TransportContext.EMPTY);

        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(Code.C205_CONTENT));
    }

    @Test
    void shouldSendObservation() throws CoapException, IOException {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);

        //when
        CompletableFuture<Boolean> ack = udpMessaging.send(ok("21C").observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));
        receive(newCoapPacket(LOCAL_1_5683).emptyAck(100));

        assertSent(newCoapPacket(LOCAL_1_5683).token(1003).mid(100).con(Code.C205_CONTENT).obs(12).payload("21C"));
        assertTrue(ack.join());
    }

    @Test
    void shouldSendObservation_andReturnFalseWhenReset() throws CoapException, IOException {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);

        //when
        CompletableFuture<Boolean> ack = udpMessaging.send(ok("21C").observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));
        receive(newCoapPacket(LOCAL_1_5683).reset(100));

        assertSent(newCoapPacket(LOCAL_1_5683).token(1003).mid(100).con(Code.C205_CONTENT).obs(12).payload("21C"));
        assertFalse(ack.join());
    }

    @Test
    void shouldFailToSendObservation_when_transportFails() throws CoapException, IOException {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(failedFuture(new IOException()));

        CompletableFuture<Boolean> ack = udpMessaging.send(ok("21C").observe(12).toSeparate(variableUInt(1003), LOCAL_1_5683));

        assertThatThrownBy(ack::join).hasCauseExactlyInstanceOf(IOException.class);
    }

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        udpMessaging.handle(coapPacketBuilder.build(), TransportContext.EMPTY);
    }

    private void receive(CoapPacket coapPacket) {
        udpMessaging.handle(coapPacket, TransportContext.EMPTY);
    }

    private void assertSent(CoapPacket coapPacket) throws CoapException, IOException {
        verify(coapTransport).sendPacket(eq(coapPacket), any(), any());
    }

    private void assertSent(CoapPacketBuilder coapPacketBuilder) throws CoapException, IOException {
        assertSent(coapPacketBuilder.build());
    }

    private CompletableFuture<Boolean> exceptionFuture() {
        return failedFuture(new IOException("no connection"));
    }

    private void initServer() throws IOException {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(observationHandler, requestService);
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

    private void udpMessagingInit(int duplicationListSize,
            ScheduledExecutorService scheduledExecutor,
            boolean isSelfCreatedExecutor,
            MessageIdSupplier idContext,
            long delayedTransactionTimeout,
            DuplicatedCoapMessageCallback duplicatedCoapMessageCallback) {
        PutOnlyMap<CoapRequestId, CoapPacket> cache = new DefaultDuplicateDetectorCache<>(
                "Default cache",
                duplicationListSize,
                CoapUdpMessaging.DEFAULT_DUPLICATION_TIMEOUT_MILLIS,
                CoapUdpMessaging.DEFAULT_CLEAN_INTERVAL_MILLIS,
                CoapUdpMessaging.DEFAULT_WARN_INTERVAL_MILLIS,
                scheduledExecutor);
        udpMessaging.init(cache,
                isSelfCreatedExecutor,
                idContext,
                delayedTransactionTimeout,
                duplicatedCoapMessageCallback,
                scheduledExecutor);
    }
}
