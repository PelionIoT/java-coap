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
        CoapPacket requestInactive = newCoapPacket(REMOTE_ADR2).mid(2).con().get().token(456).uriPath("/test").build();
        // active
        CoapTransaction activeTrans = new CoapTransaction(request, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        transMgr.put(activeTrans);

        CoapTransaction inactiveTrans = new CoapTransaction(requestInactive, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        transMgr.put(inactiveTrans);

        // active match
        assertMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).token(123).build(), activeTrans);
        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).non(Code.C205_CONTENT).token(123).build());

        // inactive match
        assertMatch(newCoapPacket(REMOTE_ADR2).mid(13).con(Code.C205_CONTENT).token(456).build(), inactiveTrans);

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
        transMgr.put(new CoapTransaction(request, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class)));

        assertNoMatch(newCoapPacket(REMOTE_ADR).mid(12).con(Code.C205_CONTENT).build());
    }


    @Test
    public void test_removeExistingActiveTransactionNoOtherTransactionsOngoing() throws Exception {
        CoapPacket ep1Request1 = newCoapPacket(REMOTE_ADR).mid(11).con().get().uriPath("/test1").build();
        CoapTransaction trans = new CoapTransaction(ep1Request1, mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));

        transMgr.put(trans);
        assertEquals(transMgr.getNumberOfTransactions(), 1);


        assertNotEmpty(transMgr.getAndRemove(trans.getTransactionId()));
        assertEquals(transMgr.getNumberOfTransactions(), 0);
    }

    @Test
    public void test_noQueueRemoveTransaction() {
        CoapTransaction trans1 = createTransaction(REMOTE_ADR, 1);

        transMgr.remove(trans1.getTransactionId());
        assertEmpty(transMgr.getAndRemove(trans1.getTransactionId()));
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
        transMgr.put(trans1);
        CoapTransaction trans2 = new CoapTransaction(newCoapPacket(REMOTE_ADR2).mid(11).con().get().uriPath("/test2").build(), mock(CoapUdpMessaging.class), TransportContext.EMPTY, mock(Consumer.class));
        transMgr.put(trans2);
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