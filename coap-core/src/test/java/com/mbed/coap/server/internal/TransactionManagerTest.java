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
package com.mbed.coap.server.internal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.exception.TooManyRequestsForEndpointException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java8.util.Optional;
import java8.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by szymon.
 */
public class TransactionManagerTest {
    private static final InetSocketAddress REMOTE_ADR = InMemoryCoapTransport.createAddress(5683);
    private static final InetSocketAddress REMOTE_ADR2 = InMemoryCoapTransport.createAddress(5685);
    private TransactionManager transMgr;

    @Before
    public void setUp() throws Exception {
        transMgr = new TransactionManager();
    }

    @Test
    public void test_findMatchForSeparateResponse() throws Exception {
        CoapPacket request = newCoapPacket(REMOTE_ADR).mid(1).con().get().token(123).uriPath("/test").build();
        CoapPacket requestInactive = newCoapPacket(REMOTE_ADR).mid(2).con().get().token(456).uriPath("/test").build();
        // active
        CoapTransaction activeTrans = new CoapTransaction(mock(RequestCallback.class), request, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class)).makeActiveForTests();
        assertTrue(transMgr.addTransactionAndGetReadyToSend(activeTrans));

        CoapTransaction inactiveTrans = new CoapTransaction(mock(RequestCallback.class), requestInactive, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
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
        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(mock(RequestCallback.class), request, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class))));

        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).build());
    }

    @Test
    public void test_addMoreThanOneTransactionForEndpoint() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapPacket ep1Request2 = newCoapPacket(REMOTE_ADR).mid(12).con().get().uriPath("/test1").build();
        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();

        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(mock(RequestCallback.class), ep1Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class))));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(mock(RequestCallback.class), ep1Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class))));

        assertEquals(transMgr.getNumberOfTransactions(), 2);

        //second ep
        assertTrue(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(mock(RequestCallback.class), ep2Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class))));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(mock(RequestCallback.class), ep2Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class))));

        assertEquals(transMgr.getNumberOfTransactions(), 4);
    }

    @Test
    public void test_removeExistingActiveTransactionNoOtherTransactionsOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapTransaction trans = new CoapTransaction(mock(RequestCallback.class), ep1Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(trans));
        assertEquals(transMgr.getNumberOfTransactions(), 1);

        trans.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(trans.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_removeExistingInactiveTransactionNoOtherTransactionOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapTransaction trans = new CoapTransaction(mock(RequestCallback.class), ep1Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));

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

        CoapTransaction ep1Trans1 = new CoapTransaction(mock(RequestCallback.class), ep1Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep1Trans2 = new CoapTransaction(mock(RequestCallback.class), ep1Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans1 = new CoapTransaction(mock(RequestCallback.class), ep2Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(mock(RequestCallback.class), ep2Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));

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

        CoapTransaction ep1Trans1 = new CoapTransaction(mock(RequestCallback.class), ep1Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep1Trans2 = new CoapTransaction(mock(RequestCallback.class), ep1Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans1 = new CoapTransaction(mock(RequestCallback.class), ep2Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(mock(RequestCallback.class), ep2Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans3 = new CoapTransaction(mock(RequestCallback.class), ep2Request3, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));

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

    @Test(expected = TooManyRequestsForEndpointException.class)
    public void test_endpointQueueOverflow() throws Exception {
        transMgr.setMaximumEndpointQueueSize(2);

        CoapPacket ep2Request1 = newCoapPacket(REMOTE_ADR2).mid(13).con().get().uriPath("/test2").build();
        CoapPacket ep2Request2 = newCoapPacket(REMOTE_ADR2).mid(14).con().get().uriPath("/test2").build();
        CoapPacket ep2Request3 = newCoapPacket(REMOTE_ADR2).mid(15).con().get().uriPath("/test2").build();

        CoapTransaction ep2Trans1 = new CoapTransaction(mock(RequestCallback.class), ep2Request1, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans2 = new CoapTransaction(mock(RequestCallback.class), ep2Request2, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
        CoapTransaction ep2Trans3 = new CoapTransaction(mock(RequestCallback.class), ep2Request3, mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));

        assertTrue(transMgr.addTransactionAndGetReadyToSend(ep2Trans1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(ep2Trans2));

        // should throw TooManyRequestsForEndpointException
        transMgr.addTransactionAndGetReadyToSend(ep2Trans3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_tooSmallQueueValue() {
        transMgr.setMaximumEndpointQueueSize(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_tooBigQueueValue() {
        transMgr.setMaximumEndpointQueueSize(65537);
    }

    @Test
    public void test_normalQueueValue() {
        transMgr.setMaximumEndpointQueueSize(1);
        transMgr.setMaximumEndpointQueueSize(100);
        transMgr.setMaximumEndpointQueueSize(65536);
    }

    @Test
    public void test_queueSorting() throws TooManyRequestsForEndpointException {
        CoapTransaction transLow1 = createTransaction(REMOTE_ADR, 1, CoapTransaction.Priority.LOW);
        CoapTransaction transLow2 = createTransaction(REMOTE_ADR, 2, CoapTransaction.Priority.LOW);
        CoapTransaction transNorm1 = createTransaction(REMOTE_ADR, 3, CoapTransaction.Priority.NORMAL);
        CoapTransaction transNorm2 = createTransaction(REMOTE_ADR, 4, CoapTransaction.Priority.NORMAL);
        CoapTransaction transHi1 = createTransaction(REMOTE_ADR, 5, CoapTransaction.Priority.HIGH);
        CoapTransaction transHi2 = createTransaction(REMOTE_ADR, 6, CoapTransaction.Priority.HIGH);

        assertTrue(transMgr.addTransactionAndGetReadyToSend(transLow1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transLow2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transNorm1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transNorm2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi2));

        assertEquals(6, transMgr.getNumberOfTransactions());

        transLow1.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transLow1.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transLow1.getTransactionId()).get().makeActiveForTests(), transHi1);

        assertNotEmpty(transMgr.removeAndLock(transHi1.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi1.getTransactionId()).get().makeActiveForTests(), transHi2);

        assertNotEmpty(transMgr.removeAndLock(transHi2.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi2.getTransactionId()).get().makeActiveForTests(), transNorm1);

        assertNotEmpty(transMgr.removeAndLock(transNorm1.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transNorm1.getTransactionId()).get().makeActiveForTests(), transNorm2);

        assertNotEmpty(transMgr.removeAndLock(transNorm2.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transNorm2.getTransactionId()).get().makeActiveForTests(), transLow2);

        assertNotEmpty(transMgr.removeAndLock(transLow2.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(transLow2.getTransactionId()));

        assertEquals(0, transMgr.getNumberOfTransactions());
    }

    @Test
    public void test_samePriorityInOrder() throws TooManyRequestsForEndpointException {
        CoapTransaction transHi1 = createTransaction(REMOTE_ADR, 1, CoapTransaction.Priority.HIGH);
        CoapTransaction transHi2 = createTransaction(REMOTE_ADR, 2, CoapTransaction.Priority.HIGH);
        CoapTransaction transHi3 = createTransaction(REMOTE_ADR, 3, CoapTransaction.Priority.NORMAL);

        assertTrue(transMgr.addTransactionAndGetReadyToSend(transHi3));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi1));

        transHi3.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transHi3.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi3.getTransactionId()).get().makeActiveForTests(), transHi2);

        assertNotEmpty(transMgr.removeAndLock(transHi2.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi2.getTransactionId()).get().makeActiveForTests(), transHi1);

        assertNotEmpty(transMgr.removeAndLock(transHi1.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(transHi1.getTransactionId()));

        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_nonExistingTransId() throws TooManyRequestsForEndpointException {
        CoapTransaction transLow1 = createTransaction(REMOTE_ADR, 1, CoapTransaction.Priority.LOW);
        CoapTransaction transLow2 = createTransaction(REMOTE_ADR, 2, CoapTransaction.Priority.LOW);
        CoapTransaction transNorm1 = createTransaction(REMOTE_ADR, 3, CoapTransaction.Priority.NORMAL);
        CoapTransaction transNorm2 = createTransaction(REMOTE_ADR, 4, CoapTransaction.Priority.NORMAL);
        CoapTransaction transHi_NOT_ADDED = createTransaction(REMOTE_ADR, 5, CoapTransaction.Priority.HIGH);
        CoapTransaction transHi2 = createTransaction(REMOTE_ADR, 6, CoapTransaction.Priority.HIGH);

        assertTrue(transMgr.addTransactionAndGetReadyToSend(transLow1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transNorm1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transLow2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transNorm2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi2));

        assertEquals(5, transMgr.getNumberOfTransactions());

        assertEmpty(transMgr.removeAndLock(transHi_NOT_ADDED.getTransactionId())); // not added transHi1

        transLow1.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transLow1.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transLow1.getTransactionId()).get(), transHi2);

        transHi2.makeActiveForTests(); // emulate sending

        assertNotEmpty(transMgr.removeAndLock(transHi2.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi2.getTransactionId()).get(), transNorm1);

        transNorm1.makeActiveForTests();


        assertFalse(transMgr.addTransactionAndGetReadyToSend(transHi_NOT_ADDED));                      // add not added trans

        assertNotEmpty(transMgr.removeAndLock(transNorm1.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transNorm1.getTransactionId()).get(), transHi_NOT_ADDED);

        transHi_NOT_ADDED.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transHi_NOT_ADDED.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transHi_NOT_ADDED.getTransactionId()).get(), transNorm2);

        transNorm2.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transNorm2.getTransactionId()));
        assertEquals(transMgr.unlockOrRemoveAndGetNext(transNorm2.getTransactionId()).get(), transLow2);

        transLow2.makeActiveForTests();

        assertNotEmpty(transMgr.removeAndLock(transLow2.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(transLow2.getTransactionId()));

        assertEquals(0, transMgr.getNumberOfTransactions());
    }

    @Test
    public void test_noQueueOverflowOnBlockTransferContinue() throws TooManyRequestsForEndpointException {
        transMgr.setMaximumEndpointQueueSize(2);
        CoapTransaction trans1 = createTransaction(REMOTE_ADR, 1, CoapTransaction.Priority.LOW);
        CoapTransaction trans2 = createTransaction(REMOTE_ADR, 2, CoapTransaction.Priority.LOW);
        CoapTransaction trans3 = createTransaction(REMOTE_ADR, 3, CoapTransaction.Priority.LOW);
        CoapTransaction trans4 = createTransaction(REMOTE_ADR, 4, CoapTransaction.Priority.LOW);

        assertTrue(transMgr.addTransactionAndGetReadyToSend(trans1));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(trans2));
        assertFalse(transMgr.addTransactionAndGetReadyToSend(trans3, true));
        try {
            assertFalse(transMgr.addTransactionAndGetReadyToSend(trans4));
            fail("should be throwh TooManyRequestsForEndpointException");
        } catch (TooManyRequestsForEndpointException e) {
        }
    }

    @Test
    public void test_noQueueRemoveTransaction() {
        CoapTransaction trans1 = createTransaction(REMOTE_ADR, 1, CoapTransaction.Priority.LOW);

        assertEmpty(transMgr.removeAndLock(trans1.getTransactionId()));
        assertEmpty(transMgr.unlockOrRemoveAndGetNext(trans1.getTransactionId()));
    }

    @Test(expected = NullPointerException.class)
    public void failWhenCallbackIsNull() throws Exception {
        new CoapTransaction(null, mock(CoapPacket.class), mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class));
    }

    @Test
    public void should_close_all_transactions_on_stop() throws TooManyRequestsForEndpointException {
        RequestCallback callback1 = mock(RequestCallback.class);
        RequestCallback callback2 = mock(RequestCallback.class);

        //given, two transactions
        transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(callback1, newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build(), mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class)));
        transMgr.addTransactionAndGetReadyToSend(new CoapTransaction(callback2, newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test2").build(), mock(CoapUdpMessaging.class), TransportContext.NULL, mock(Consumer.class)));
        assertEquals(2, transMgr.getNumberOfTransactions());


        //when
        transMgr.close();

        //then
        verify(callback1).callException(isA(IOException.class));
        verify(callback2).callException(isA(IOException.class));
    }

    private CoapTransaction createTransaction(InetSocketAddress remote, int mid, CoapTransaction.Priority priority) {
        CoapPacket packet = newCoapPacket(remote).mid(mid).con().get().uriPath("/").build();
        return new CoapTransaction(mock(RequestCallback.class), packet, mock(CoapUdpMessaging.class), TransportContext.NULL, priority, mock(Consumer.class));
    }

    private void assertNoMatch(CoapPacket packet) {
        assertEmpty(transMgr.findMatchAndRemoveForSeparateResponse(packet));
    }

    private void assertMatch(CoapPacket packet, CoapTransaction expected) {
        Optional<CoapTransaction> actual = transMgr.findMatchAndRemoveForSeparateResponse(packet);
        assertNotEmpty(actual);
        assertEquals(actual.get(), expected);
    }

    public static void assertPresent(Optional val) {
        assertTrue(val.isPresent());
    }

    public static void assertEmpty(Optional val) {
        assertFalse(val.isPresent());
    }

    public static void assertNotEmpty(Optional val) {
        assertTrue(val.isPresent());
    }
}