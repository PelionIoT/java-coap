/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.utils;

import static org.junit.Assert.*;
import org.junit.Test;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;

/**
 * Created by szymon.
 */
public class EventLoggerCoapPacketTest {

    @Test
    public void shouldPrintBeginningOfAPayload() throws Exception {
        //short
        CoapPacket packet = new CoapPacket(Code.C205_CONTENT, MessageType.Acknowledgement, null);
        packet.setPayload("*********|*********");

        assertEquals("ACK 205 MID:0 pl(19):0x2a2a2a2a2a2a2a2a2a7c2a2a2a2a2a2a2a2a2a", new EventLoggerCoapPacket(packet).toString());

        //long
        packet = new CoapPacket(Code.C205_CONTENT, MessageType.Acknowledgement, null);
        packet.setPayload("*********|*********|*********|*********|");

        assertEquals("ACK 205 MID:0 pl(40):0x2a2a2a2a2a2a2a2a2a7c2a2a2a2a2a2a2a2a2a7c2a2a..", new EventLoggerCoapPacket(packet).toString());

    }
}