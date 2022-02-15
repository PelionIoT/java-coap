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
package com.mbed.coap.server.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapRequestId;
import com.mbed.coap.server.DuplicatedCoapMessageCallback;
import com.mbed.coap.server.MessageIdSupplier;
import com.mbed.coap.server.PutOnlyMap;
import com.mbed.coap.server.internal.CoapTransaction.Priority;
import com.mbed.coap.transmission.CoapTimeout;
import com.mbed.coap.transmission.TransmissionTimeout;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import protocolTests.utils.CoapPacketBuilder;

/**
 * Created by szymon
 */
public class CoapUdpMessagingTest {

    private final CoapTransport coapTransport = mock(CoapTransport.class);
    private CoapRequestHandler requestHandler = mock(CoapRequestHandler.class);
    private int mid = 100;
    private final MessageIdSupplier midSupplier = () -> mid++;
    private CoapUdpMessaging udpMessaging;
    private ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);


    @Before
    public void setUp() throws Exception {
        reset(requestHandler);

        udpMessaging = new CoapUdpMessaging(coapTransport);

        resetCoapTransport();
    }

    private void resetCoapTransport() {
        reset(coapTransport);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void failWhenNoTransportIsProvided() throws Exception {
        assertThatThrownBy(() -> udpMessagingInit(1, null, false, null, 1, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> udpMessagingInit(1, null, false, null, 1, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> udpMessagingInit(1, scheduledExecutor, false, null, 1, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> udpMessagingInit(1, scheduledExecutor, false, () -> 1, 1, null, 0, DuplicatedCoapMessageCallback.NULL))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    public void doNotStopScheduleExecutor() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, mock(MessageIdSupplier.class), 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);
        assertEquals(scheduledExecutor, udpMessaging.getScheduledExecutor());

        udpMessaging.stop();
        verify(scheduledExecutor, never()).shutdown();
    }

    @Test
    public void shouldFailToMakeRequest_whenQueueIsFull() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        CompletableFuture<CoapPacket> resp1 = makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/10"));
        assertFalse(resp1.isDone());

        //should fail to make a request
        CompletableFuture<CoapPacket> resp2 = makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/11"));

        assertTrue(resp2.isDone());
        assertTrue(resp2.isCompletedExceptionally());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(TooManyRequestsForEndpointException.class);
    }

    @Test
    public void shouldSucceedToMakeRequest_whenQueueIsFull_forceAdd() throws Exception {
        //init with max queue size of 1
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        CompletableFuture<CoapPacket> resp1 = makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/10"));
        assertFalse(resp1.isDone());

        //should not fail to make a request
        FutureCallbackAdapter<CoapPacket> resp2 = new FutureCallbackAdapter<>();

        udpMessaging.makePrioritisedRequest(newCoapPacket(LOCAL_5683).get().uriPath("/11").build(), resp2, TransportContext.NULL);

        assertFalse(resp1.isDone());
        assertFalse(resp2.isCompletedExceptionally());
    }

    @Test
    public void failToMakeRequestWhenMissingParameters() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        //missing address
        assertThatThrownBy(() ->
                udpMessaging.makeRequest(newCoapPacket(123).get().uriPath("/10").build(), mock(Callback.class), TransportContext.NULL)
        ).isExactlyInstanceOf(NullPointerException.class);

        //missing coap packet
        assertThatThrownBy(() ->
                udpMessaging.makeRequest(null, mock(Callback.class), TransportContext.NULL)
        ).isExactlyInstanceOf(NullPointerException.class);

        //missing callback
        assertThatThrownBy(() ->
                udpMessaging.makeRequest(newCoapPacket(LOCAL_5683).get().uriPath("/10").build(), ((Callback) null), TransportContext.NULL)
        ).isExactlyInstanceOf(NullPointerException.class);

    }

    @Test
    public void responseToPingMessage() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).con(null));

        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).reset());
    }

    @Test
    public void ignore_nonProcessedMessage() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        receive(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C203_VALID));
        verify(coapTransport, never()).sendPacket(any(), any(), any());

        receive(newCoapPacket(LOCAL_1_5683).reset(1));
        verify(coapTransport, never()).sendPacket(any(), any(), any());
    }

    @Test
    public void duplicateRequest() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();
        receive(req);
        udpMessaging.sendResponse(req, newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0").build());
        resetCoapTransport();

        //duplicate
        receive(req);
        assertSent(newCoapPacket(LOCAL_1_5683).mid(1).ack(Code.C205_CONTENT).payload("ABC0"));
    }

    @Test
    public void duplicateResponseToGetRequest() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);
        CoapPacketBuilder reqPacket = newCoapPacket(LOCAL_5683).get().uriPath("/10").obs(0).token(33);
        CompletableFuture<CoapPacket> resp1 = makeRequest(reqPacket);
        assertFalse(resp1.isDone());
        CoapPacket respPacket = newCoapPacket(LOCAL_5683).mid(100).obs(0).ack(Code.C205_CONTENT).token(33).payload("A").build();
        receive(respPacket);
        assertTrue(resp1.isDone());
        receive(respPacket);
        verify(requestHandler, times(0)).handleObservation(any(), any());
    }

    @Test
    public void duplicateRequest_noDuplicateDetector() throws Exception {
        udpMessagingInit(0, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        CoapPacket req = newCoapPacket(LOCAL_1_5683).mid(1).con().delete().uriPath("/19").build();

        receive(req);
        receive(req);

        verify(requestHandler, times(2)).handleRequest(eq(req), any());
    }

    @Test
    public void non_request_response() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);

        //request
        CompletableFuture<CoapPacket> resp = makeRequest(newCoapPacket(LOCAL_5683).mid(1).token(1001).non().get());

        //response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).non(Code.C203_VALID));

        assertEquals(Code.C203_VALID, resp.get().getCode());
    }

    @Test
    public void separate_confirmable_response() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);

        //request
        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).mid(100).token(1001).con().get();
        CompletableFuture<CoapPacket> resp = makeRequest(req);
        assertSent(req);

        //response - empty ack
        receive(newCoapPacket(LOCAL_5683).emptyAck(100));

        //separate confirmable response
        receive(newCoapPacket(LOCAL_5683).mid(2).token(1001).con(Code.C205_CONTENT));
        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(null));

        assertEquals(Code.C205_CONTENT, resp.get().getCode());
    }


    //    @Test
    //    public void networkError_whileHandlingRequest() throws Exception {
    //        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, blockSize, 120000, DuplicatedCoapMessageCallback.NULL);
    //
    ////        server.addRequestHandler("/19", new ReadOnlyCoapResource("ABC"));
    //
    //        udpMessaging.sendResponse();
    //
    //        //IOException
    //        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
    //        receive(newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/19"));
    //        verify(coapTransport, only()).sendPacket(any(), any(), any());
    //
    //        //IOException
    //        resetCoapTransport();
    //        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
    //        receive(newCoapPacket(LOCAL_1_5683).mid(1).con().put().uriPath("/19"));
    //        verify(coapTransport, only()).sendPacket(any(), any(), any());
    //
    //    }

    @Test
    public void sendRetransmissions() throws Exception {
        initServer();
        udpMessaging.setTransmissionTimeout(new TestTransmissionTimeout(2));

        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).get().uriPath("/10");
        CompletableFuture<CoapPacket> resp = makeRequest(req);

        Thread.sleep(1);
        udpMessaging.resendTimeouts();
        Thread.sleep(1);
        udpMessaging.resendTimeouts();

        verify(coapTransport, times(2)).sendPacket(eq(req.build()), any(), any());

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(CoapTimeoutException.class);
    }

    @Test
    public void networkFail_whenRetransmissions() throws Exception {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 1, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.setTransmissionTimeout(new CoapTimeout(1, 5));

        CoapPacketBuilder req = newCoapPacket(LOCAL_5683).get().uriPath("/10");
        CompletableFuture<CoapPacket> resp = makeRequest(req);

        Thread.sleep(1);

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        udpMessaging.resendTimeouts();

        assertTrue(resp.isCompletedExceptionally());
        assertThatThrownBy(resp::get).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void shouldFail_toMakeSecondRequestFromQueue() throws Exception {
        initServer();

        CompletableFuture<CoapPacket> resp1 = makeRequest(newCoapPacket(LOCAL_5683).mid(100).get().uriPath("/10"));
        CompletableFuture<CoapPacket> resp2 = makeRequest(newCoapPacket(LOCAL_5683).mid(101).get().uriPath("/11"));

        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());
        receive(newCoapPacket(LOCAL_5683).mid(100).ack(Code.C205_CONTENT).payload("ABC"));

        assertEquals("ABC", resp1.get().getPayloadString());
        assertThatThrownBy(resp2::get).hasCauseExactlyInstanceOf(IOException.class);
    }


    @Test
    public void network_fail_when_sending_NON_request() throws Exception {
        initServer();

        Callback<CoapPacket> callback = mock(Callback.class);
        given(coapTransport.sendPacket(any(), any(), any())).willReturn(exceptionFuture());

        udpMessaging.makeRequest(newCoapPacket(LOCAL_1_5683).non().token(12).get().uriPath("/test").build(), callback, TransportContext.NULL);

        verify(callback).callException(any());
    }

    // --- OBSERVATIONS ---

    @Test
    public void receiveObservation() throws Exception {
        initServer();

        receive(newCoapPacket(LOCAL_5683).mid(3001).obs(2).con(Code.C203_VALID).token(33).payload("A"));
        receive(newCoapPacket(LOCAL_5683).mid(3002).obs(2).con(Code.C205_CONTENT).token(44).payload("B"));

        verify(requestHandler, times(2)).handleObservation(any(), any());
    }

    @Test
    public void shouldSetMessageIdOnMakeRequest() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.makeRequest(newCoapPacket(LOCAL_5683).mid(0).get().uriPath("/test").build(), Callback.ignore(), TransportContext.NULL);

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).get().uriPath("/test"));
    }

    @Test
    public void shouldSetMessageIdOnSendNonResponse() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(0).build(), newCoapPacket(LOCAL_5683).mid(0).non(Code.C205_CONTENT).build(), TransportContext.NULL);

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).non(Code.C205_CONTENT));
    }

    @Test
    public void shouldSetMessageIdOnSend_resetResponse_toNonRequest() throws IOException, CoapException {
        initServer();
        mid = 1000;

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(0).non().get().build(), newCoapPacket(LOCAL_5683).mid(0).reset().build(), TransportContext.NULL);

        assertSent(newCoapPacket(LOCAL_5683).mid(1000).reset());
    }

    @Test
    public void shouldNotSetMessageId_onSendAckResponse() throws IOException, CoapException {
        initServer();

        udpMessaging.sendResponse(newCoapPacket(LOCAL_5683).mid(2).build(), newCoapPacket(LOCAL_5683).mid(2).ack(Code.C205_CONTENT).build(), TransportContext.NULL);

        assertSent(newCoapPacket(LOCAL_5683).mid(2).ack(Code.C205_CONTENT));
    }

    private void receive(CoapPacketBuilder coapPacketBuilder) {
        udpMessaging.handle(coapPacketBuilder.build(), TransportContext.NULL);
    }

    private void receive(CoapPacket coapPacket) {
        udpMessaging.handle(coapPacket, TransportContext.NULL);
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

    private void initServer() throws IOException {
        udpMessagingInit(10, scheduledExecutor, false, midSupplier, 10, Priority.NORMAL, 0, DuplicatedCoapMessageCallback.NULL);
        udpMessaging.start(requestHandler);
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

    private CompletableFuture<CoapPacket> makeRequest(CoapPacketBuilder coapPacket) {
        FutureCallbackAdapter<CoapPacket> completableFuture = new FutureCallbackAdapter<>();

        udpMessaging.makeRequest(coapPacket.build(), completableFuture, TransportContext.NULL);
        return completableFuture;
    }

    private void udpMessagingInit(int duplicationListSize,
            ScheduledExecutorService scheduledExecutor,
            boolean isSelfCreatedExecutor,
            MessageIdSupplier idContext,
            int maxQueueSize,
            CoapTransaction.Priority defaultPriority,
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
                maxQueueSize,
                defaultPriority,
                delayedTransactionTimeout,
                duplicatedCoapMessageCallback,
                scheduledExecutor);
    }
}
