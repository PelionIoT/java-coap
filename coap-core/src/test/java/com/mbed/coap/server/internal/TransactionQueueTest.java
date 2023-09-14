/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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

import static com.mbed.coap.server.internal.TransactionManagerTest.*;
import static org.junit.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.server.internal.CoapTransaction.Priority;
import com.mbed.coap.transport.InMemoryCoapTransport;
import com.mbed.coap.utils.RequestCallback;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/**
 * Created by szymon
 */
public class TransactionQueueTest {
    private static final InetSocketAddress REMOTE_ADR = InMemoryCoapTransport.createAddress(5683);

    @Test
    public void shouldAdd_defaultPriority() throws Exception {
        AtomicBoolean queueOverflow = new AtomicBoolean();

        TransactionQueue transQueue = TransactionQueue.of(newTransaction(12));
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 12);

        transQueue = transQueue.add(newTransaction(13), false, 100, queueOverflow);
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 12);

        transQueue = transQueue.add(newTransaction(14), false, 100, queueOverflow);
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 12);

        assertEquals(transQueue.size(), 3);
    }

    @Test
    public void shouldAdd_priority() throws Exception {
        AtomicBoolean queueOverflow = new AtomicBoolean();

        TransactionQueue transQueue = TransactionQueue.of(newTransaction(12));

        transQueue = transQueue.add(newTransaction(13, Priority.LOW), false, 100, queueOverflow);
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 12);

        transQueue = transQueue.add(newTransaction(14, Priority.HIGH), false, 100, queueOverflow);
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 14);

        transQueue = transQueue.add(newTransaction(15, Priority.HIGH), false, 100, queueOverflow);
        assertEquals(transQueue.head().get().getCoapRequest().getMessageId(), 14);
    }

    @Test
    public void shouldAdd_overlimit_and_force() throws Exception {
        AtomicBoolean queueOverflow = new AtomicBoolean();

        TransactionQueue transQueue = TransactionQueue
                .of(newTransaction(1))
                .add(newTransaction(2), false, 100, queueOverflow)
                .add(newTransaction(3), false, 100, queueOverflow)
                .add(newTransaction(4), false, 100, queueOverflow);


        //on edge
        queueOverflow.set(false);
        assertEquals(transQueue.add(newTransaction(5), false, 5, queueOverflow).size(), 5);
        assertFalse(queueOverflow.get());

        //too small - overflow
        queueOverflow.set(false);
        assertEquals(transQueue.add(newTransaction(5), false, 3, queueOverflow), transQueue);
        assertTrue(queueOverflow.get());

        queueOverflow.set(false);
        assertEquals(transQueue.add(newTransaction(5), false, 4, queueOverflow), transQueue);
        assertTrue(queueOverflow.get());

        //too small with locked - single - overflow
        queueOverflow.set(false);
        TransactionQueue emptyLocked = TransactionQueue.of().lock(newTransId(1));
        assertEquals(emptyLocked.add(newTransaction(6), false, 1, queueOverflow), emptyLocked);
        assertTrue(queueOverflow.get());

        //too small with locked - overflow
        queueOverflow.set(false);
        TransactionQueue lockedQueue = transQueue.lock(newTransId(10));
        assertEquals(lockedQueue.add(newTransaction(6), false, 5, queueOverflow), lockedQueue);
        assertTrue(queueOverflow.get());

        //force
        queueOverflow.set(false);
        assertEquals(transQueue.add(newTransaction(5), true, 1, queueOverflow).size(), 5);
        assertFalse(queueOverflow.get());
    }

    @Test
    public void shouldRemoveAndLock() throws Exception {
        AtomicBoolean queueOverflow = new AtomicBoolean();

        TransactionQueue transQueue = TransactionQueue
                .of(newTransaction(1))
                .add(newTransaction(2).makeActiveForTests(), false, 100, queueOverflow)
                .add(newTransaction(3), false, 100, queueOverflow)
                .add(newTransaction(4), false, 100, queueOverflow);


        //found and active
        TransactionQueue tq = transQueue.removeAndLock(newTransId(2)).transactionQueue;
        assertFalse(tq.notLocked());
        assertEquals(tq.size(), 3);

        //not found
        tq = transQueue.removeAndLock(newTransId(123)).transactionQueue;
        assertTrue(tq.notLocked());
        assertEquals(tq.size(), 4);

        //single queue - found active
        tq = TransactionQueue.of(newTransaction(1).makeActiveForTests()).removeAndLock(newTransId(1)).transactionQueue;
        assertFalse(tq.notLocked());
        assertEquals(tq.size(), 0);

        //single queue - not found
        tq = TransactionQueue.of(newTransaction(1)).removeAndLock(newTransId(1432)).transactionQueue;
        assertTrue(tq.notLocked());
        assertEquals(tq.size(), 1);

        //single queue - not found even if active
        tq = TransactionQueue.of(newTransaction(1).makeActiveForTests()).removeAndLock(newTransId(1432)).transactionQueue;
        assertTrue(tq.notLocked());
        assertEquals(tq.size(), 1);

        //not found because not active
        tq = TransactionQueue.of(newTransaction(1)).removeAndLock(newTransId(1)).transactionQueue;
        assertTrue(tq.notLocked());
        assertEquals(tq.size(), 1);
    }

    @Test
    public void shouldUnlock() throws Exception {
        TransactionQueue transQueue = TransactionQueue
                .of(newTransaction(1))
                .lock(newTransId(2));

        //matching
        assertTrue(transQueue.unlockOrRemove(newTransId(2)).get().notLocked());

        //not matching
        assertEquals(transQueue.unlockOrRemove(newTransId(122)).get(), transQueue);

        //if empty, return null
        TransactionQueue emptyLocked = TransactionQueue.of().lock(newTransId(3));
        assertEmpty(emptyLocked.unlockOrRemove(newTransId(3)));

        //locked but with containing transaction
        TransactionQueue queue2 = TransactionQueue.of(newTransaction(1)).add(newTransaction(2).makeActiveForTests(), false, 10, new AtomicBoolean()).lock(newTransId(100));
        assertEquals(queue2.unlockOrRemove(newTransId(2)).get().size(), 1);
        assertFalse(queue2.unlockOrRemove(newTransId(2)).get().notLocked());

        //non locked but with containing transaction
        TransactionQueue queue3 = TransactionQueue.of(newTransaction(1).makeActiveForTests()).add(newTransaction(2).makeActiveForTests(), false, 10, new AtomicBoolean());
        assertEquals(queue3.unlockOrRemove(newTransId(1)).get().size(), 1);
        assertEquals(queue3.unlockOrRemove(newTransId(2)).get().size(), 1);

        //non locked empty
        assertEmpty(TransactionQueue.of(newTransaction(1).makeActiveForTests()).unlockOrRemove(newTransId(1)));
    }

    private CoapTransaction newTransaction(int mid) {
        return new CoapTransaction(RequestCallback.NULL, newCoapPacket(REMOTE_ADR).mid(mid).build(), null, null, null);
    }

    private CoapTransactionId newTransId(int mid) {
        return new CoapTransactionId(newCoapPacket(REMOTE_ADR).mid(mid).build());
    }

    private CoapTransaction newTransaction(int mid, Priority priority) {
        return new CoapTransaction(RequestCallback.NULL, newCoapPacket(REMOTE_ADR).mid(mid).build(), null, null, priority, null);
    }
}