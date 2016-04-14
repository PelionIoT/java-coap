/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import java.net.InetSocketAddress;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.server.CoapServerObserve;
import org.mbed.coap.transport.InMemoryTransport;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.Callback;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by szymon.
 */
public class TransactionManagerTest {
    private static final InetSocketAddress REMOVE_ADR = InMemoryTransport.createAddress(5683);
    private TransactionManager transMgr;

    @BeforeMethod
    public void setUp() throws Exception {
        transMgr = new TransactionManager();
    }

    @Test
    public void test_findMatchForSeparateResponse() throws Exception {
        CoapPacket request = newCoapPacket(REMOVE_ADR).mid(1).con().get().token(123).uriPath("/test").build();
        transMgr.add(new CoapTransaction(mock(Callback.class), request, mock(CoapServerObserve.class), TransportContext.NULL));

        assertMatch(newCoapPacket(REMOVE_ADR).mid(12).con(Code.C205_CONTENT).token(123).build());
        assertMatch(newCoapPacket(REMOVE_ADR).mid(12).non(Code.C205_CONTENT).token(123).build());

        //failures

        //message type: ACK
        assertNoMatch(newCoapPacket(REMOVE_ADR).mid(12).ack(Code.C205_CONTENT).token(123).build());

        //wrong token
        assertNoMatch(newCoapPacket(REMOVE_ADR).mid(12).con(Code.C205_CONTENT).token(12).build());

        //wrong address
        assertNoMatch(newCoapPacket(InMemoryTransport.createAddress(61616)).mid(12).con(Code.C205_CONTENT).token(123).build());

        //another request
        assertNoMatch(newCoapPacket(REMOVE_ADR).mid(12).con().get().token(123).build());
    }

    @Test
    public void test_findMatchForSeparateResponse_emptyToken() throws Exception {
        CoapPacket request = newCoapPacket(REMOVE_ADR).mid(1).con().get().uriPath("/test").build();
        transMgr.add(new CoapTransaction(mock(Callback.class), request, mock(CoapServerObserve.class), TransportContext.NULL));

        assertNoMatch(newCoapPacket(REMOVE_ADR).mid(12).con(Code.C205_CONTENT).build());
    }

    private void assertNoMatch(CoapPacket packet) {
        assertNull(transMgr.findMatchForSeparateResponse(packet));
    }

    private void assertMatch(CoapPacket packet) {
        assertNotNull(transMgr.findMatchForSeparateResponse(packet));
    }
}