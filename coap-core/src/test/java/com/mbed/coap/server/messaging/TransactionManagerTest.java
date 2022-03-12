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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionManagerTest {
    private static final InetSocketAddress REMOTE_ADR = InMemoryCoapTransport.createAddress(5683);
    private static final InetSocketAddress REMOTE_ADR2 = InMemoryCoapTransport.createAddress(5685);
    private TransactionManager transMgr;

    @BeforeEach
    public void setUp() throws Exception {
        transMgr = new TransactionManager();
    }

    @Test
    public void test_findMatchForSeparateResponse() throws Exception {
        CoapPacket request = newCoapPacket(REMOTE_ADR).mid(1).con().get().token(123).uriPath("/test").build();
        CoapPacket requestInactive = newCoapPacket(REMOTE_ADR).mid(2).con().get().token(456).uriPath("/test").build();
        // active
        CoapTransaction activeTrans = new CoapTransaction(request, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class)).makeActiveForTests();
        assertTrue(transMgr.addTransactionAndGetReadyToSend(activeTrans));

        CoapTransaction inactiveTrans = new CoapTransaction(requestInactive, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(inactiveTrans));

        // active match
        assertMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).token(123).build(), activeTrans);
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).non(Code.C205_CONTENT).token(123).build());

        // inactive match
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(13).con(Code.C205_CONTENT).token(456).build());

        //failures

        //message type: ACK
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).ack(Code.C205_CONTENT).token(123).build());

        //wrong token
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).token(12).build());

        //wrong address
        assertNoMatch(newCoapPacket(InMemoryCoapTransport.createAddress(61616)).mid(12).con(Code.C205_CONTENT).token(123).build());

        //another request
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).con().get().token(123).build());
    }

    @Test
    public void test_findMatchForSeparateResponse_emptyToken() throws Exception {
        CoapPacket request = newCoapPacket(REMOTE_ADR).mid(1).con().get().uriPath("/test").build();
        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(request, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))));

        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).build());
    }

    @Test
    public void test_addMoreThanOneTransactionForEndpoint() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapPacket ep1Request2 = newCoapPacket(REMOTE_ADR).mid(12).con().get().uriPath("/test1").build();
        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();

        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(ep1Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))));

        assertEquals(transMgr.getNumberOfTransactions(), 2);

        //second ep
        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(ep2Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(ep2Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))));

        assertEquals(transMgr.getNumberOfTransactions(), 4);
    }

    @Test
    public void test_removeExistingActiveTransactionNoOtherTransactionsOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapTransaction trans = new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(trans));
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        trans.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(trans.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_removeExistingInactiveTransactionNoOtherTransactionOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapTransaction trans = new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(trans));
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        assertEmpty(transMgr.removeAndLock(trans.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        trans.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(trans.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_removeExistingTransactionOneTransactionOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapPacket ep1Request2 = newCoapPacket(REMOTE_ADR).mid(12).con().get().uriPath("/test1").build();
        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();

        CoapTransaction ep1Trans1 = new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep1Trans2 = new CoapTransaction(ep1Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans1 = new CoapTransaction(ep2Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(ep2Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep1Trans1));
        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep2Trans1));

        assertEquals(transMgr.getNumberOfTransactions(), 2);

        assertFalse(transMgr.addTransactionAndGetReadyToSend(ep2Trans2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(ep1Trans2));

        assertEquals(transMgr.getNumberOfTransactions(), 4);

        ep2Trans1.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(ep2Trans1.getTransactionId()));
        CoapTransaction next = transMgr.unlockOrRemoveAndGetNext(ep2Trans1.getTransactionId()).get();
        assertNotNull(next);
        assertEquals(next, ep2Trans2);
        assertEquals(transMgr.getNumberOfTransactions(), 3);

        next.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(ep2Trans2.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(ep2Trans2.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 2);

        ep1Trans1.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(ep1Trans1.getTransactionId()));
        next = transMgr.unlockOrRemoveAndGetNext(ep1Trans1.getTransactionId()).get();
        assertNotNull(next);
        assertEquals(next, ep1Trans2);
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        next.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(ep1Trans2.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(ep1Trans2.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void removeNotFoundTransaction() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapPacket ep1Request2 = newCoapPacket(REMOTE_ADR).mid(12).con().get().uriPath("/test1").build();
        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();
        CoapPacket ep2Request3 = newCoapPacket(REMOTE_ADR2).mid(15).con().get().uriPath("/test2").build();

        CoapTransaction ep1Trans1 = new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep1Trans2 = new CoapTransaction(ep1Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans1 = new CoapTransaction(ep2Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(ep2Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans3 = new CoapTransaction(ep2Request3, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep1Trans1));
        assertEmpty(transMgr.removeAndLock(ep1Trans2.getTransactionId()));
        CoapTransaction next = transMgr.unlockOrRemoveAndGetNext(ep1Trans2.getTransactionId()).get(); // not added
        assertEquals(next, ep1Trans1); // return first for this endpoint

        ep1Trans1.makeActiveForTests(); // emulate sending

        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep2Trans1));
        ep2Trans1.makeActiveForTests();
        assertFalse(transMgr.addTransactionAndGetReadyToSend(ep2Trans2));

        assertEquals(transMgr.getNumberOfTransactions(), 3);

        assertEmpty(transMgr.removeAndLock(ep2Trans3.getTransactionId()));
        next = transMgr.unlockOrRemoveAndGetNext(ep2Trans3.getTransactionId()).get(); // not added
        assertEquals(next, ep2Trans1); // return first for this endpoint
        assertEquals(transMgr.getNumberOfTransactions(), 3);


        assertNotEmpty(transMgr.removeAndLock(ep2Trans1.getTransactionId()));
        next = transMgr.unlockOrRemoveAndGetNext(ep2Trans1.getTransactionId()).get();
        assertNotNull(next);
        assertEquals(next, ep2Trans2);
        assertEquals(transMgr.getNumberOfTransactions(), 2);

        ep2Trans2.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(ep2Trans2.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(ep2Trans2.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        assertNotEmpty(transMgr.removeAndLock(ep1Trans1.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(ep1Trans1.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_endpointQueueOverflow() throws Exception {
        transMgr.setMaximumEndpointQueueSize(2);

        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();
        CoapPacket ep2Request3 = newCoapPacket(REMOTE_ADR2).mid(15).con().get().uriPath("/test2").build();

        CoapTransaction ep2Trans1 = new CoapTransaction(ep2Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(ep2Request2, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        CoapTransaction ep2Trans3 = new CoapTransaction(ep2Request3, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep2Trans1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(ep2Trans2));

        // should throw TooManyRequestsForEndpointException
        assertThrows(TooManyRequestsForEndpointException.class, () ->
                transMgr.addTransactionAndGetReadyToSend(ep2Trans3)
        );
    }

    @Test
    public void test_tooSmallQueueValue() {
        assertThrows(IllegalArgumentException.class, () ->
                transMgr.setMaximumEndpointQueueSize(0)
        );
    }

    @Test
    public void test_tooBigQueueValue() {
        assertThrows(IllegalArgumentException.class, () ->
                transMgr.setMaximumEndpointQueueSize(65537)
        );
    }

    @Test
    public void test_normalQueueValue() {
        transMgr.setMaximumEndpointQueueSize(1);
        transMgr.setMaximumEndpointQueueSize(100);
        transMgr.setMaximumEndpointQueueSize(65536);
    }

    @Test
    public void test_noQueueOverflowOnBlockTransferContinue() throws TooManyRequestsForEndpointException {
        transMgr.setMaximumEndpointQueueSize(2);
        CoapTransaction trans1 = createTransaction(REMOTE_ADR, 1);
        CoapTransaction trans2 = createTransaction(REMOTE_ADR, 2);
        CoapTransaction trans3 = createTransaction(REMOTE_ADR, 3);
        CoapTransaction trans4 = createTransaction(REMOTE_ADR, 4);

        assertTrue(transMgr.addTransactionAndGetReadyToSend(trans1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(trans2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(trans3, true));
        assertThrows(TooManyRequestsForEndpointException.class, () -> transMgr.addTransactionAndGetReadyToSend(trans4));
    }

    @Test
    public void test_noQueueRemoveTransaction() {
        CoapTransaction trans1 = createTransaction(REMOTE_ADR, 1);

        assertEmpty(transMgr.removeAndLock(trans1.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(trans1.getTransactionId()));
    }

    @Test
    public void failWhenCallbackIsNull() throws Exception {
        assertThrows(NullPointerException.class, () ->
                new CoapTransaction(null, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class))
        );
    }

    @Test
    public void should_close_all_transactions_on_stop() throws TooManyRequestsForEndpointException {
        //given, two transactions
        CoapTransaction trans1 = new CoapTransaction(newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build(), mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        transMgr.addTransactionAndGetReadyToSend(trans1);
        CoapTransaction trans2 = new CoapTransaction(newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test2").build(), mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        transMgr.addTransactionAndGetReadyToSend(trans2);
        assertEquals(2, transMgr.getNumberOfTransactions());


        //when
        transMgr.close();

        //then
        assertThatThrownBy(() -> trans1.promise.get()).hasCauseExactlyInstanceOf(IOException.class);
        assertThatThrownBy(() -> trans2.promise.get()).hasCauseExactlyInstanceOf(IOException.class);
    }

    private CoapTransaction createTransaction(InetSocketAddress remote, int mid) {
        CoapPacket packet = newCoapPacket(remote).mid(mid).con().get().uriPath("/").build();
        return new CoapTransaction(packet, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
    }

    private void assertNoMatch(CoapPacket packet) {
        assertEmpty(transMgr.findMatchAndRemoveForSeparateResponse(packet));
    }

    private void assertMatch(CoapPacket packet, CoapTransaction expected) {
        Optional<CoapTransaction> actual = transMgr.findMatchAndRemoveForSeparateResponse(packet);
        assertNotEmpty(actual);
        assertEquals(actual.get(), expected);
    }

    public static void assertEmpty(Optional val) {
        assertFalse(val.isPresent());
    }

    public static void assertNotEmpty(Optional val) {
        assertTrue(val.isPresent());
    }
}