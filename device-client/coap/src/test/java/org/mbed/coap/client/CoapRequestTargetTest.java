/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.client;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import java.net.InetSocketAddress;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.testng.annotations.Test;

/**
 * @author szymon
 */
public class CoapRequestTargetTest {

    @Test
    public void test() {
        InetSocketAddress destination = new InetSocketAddress("localhost", 5683);
        CoapClient c = when(mock(CoapClient.class).getDestination()).thenReturn(destination).getMock();

        CoapRequestTarget req = new CoapRequestTarget("/0/1/2", c);

        req.accept((short) 1);
        req.blockSize(BlockSize.S_16);
        req.etag(new byte[]{10, 8, 6});
        req.host("arm.com");
        req.ifMatch(new byte[]{9, 7, 5});
        req.ifNotMatch(false);
        req.maxAge(789456L);
        req.non();
        req.payload("perse");
        req.query("p=1");
        req.query("b", "2");
        req.token(45463L);

        CoapPacket packet = new CoapPacket(Method.GET, MessageType.NonConfirmable, "/0/1/2", destination);
        packet.headers().setAccept(new short[]{(short) 1});
        packet.headers().setEtag(new byte[]{10, 8, 6});
        packet.headers().setUriHost("arm.com");
        packet.headers().setIfMatch(new byte[][]{new byte[]{9, 7, 5}});
        packet.headers().setIfNonMatch(Boolean.FALSE);
        packet.headers().setMaxAge(789456L);
        packet.headers().setUriQuery("p=1&b=2");
        packet.setToken(new byte[]{(byte) 0xB1, (byte) 0x97});
        packet.setPayload("perse");

        assertEquals(packet, req.getRequestPacket());
        //
        req.ifNotMatch();
        req.con();

        packet.setMessageType(MessageType.Confirmable);
        packet.headers().setIfNonMatch(Boolean.TRUE);

        assertEquals(packet, req.getRequestPacket());
    }

    @Test
    public void malformedUriQuery() {
        CoapRequestTarget req = new CoapRequestTarget("/0/1/2", mock(CoapClient.class));
        failQueryWithNonValidChars(req, "", "2");
        failQueryWithNonValidChars(req, "&", "2");
        failQueryWithNonValidChars(req, "=", "54");
        failQueryWithNonValidChars(req, "f", "");
        failQueryWithNonValidChars(req, "f", "&");
        failQueryWithNonValidChars(req, "f", "=");

    }

    private static void failQueryWithNonValidChars(CoapRequestTarget req, String name, String val) {
        try {
            req.query(name, val);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Non valid characters provided in query", e.getMessage());
        }
    }
}
