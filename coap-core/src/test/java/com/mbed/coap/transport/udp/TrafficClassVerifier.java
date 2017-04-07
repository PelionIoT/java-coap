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
package com.mbed.coap.transport.udp;

import static org.junit.Assert.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import org.junit.Test;

/**
 * IPv6 traffic-class verification class, not ment for regular unit testing.
 *
 * @author szymon
 */
public class TrafficClassVerifier {

    private static final byte[] DATA = new byte[]{1, 2, 3};
    private static final String DESTINATION = "::1";

    @Test
    public void datagramSocket() throws Exception {
        DatagramSocket socket = new DatagramSocket(0);

        socket.setTrafficClass(0x10);
        socket.send(new DatagramPacket(DATA, 3, InetAddress.getByName(DESTINATION), 5683));
        System.out.println("VERIFY IN WIRESHARK THAT PACKET'S TRAFFIC-CLASS IS SET!");

        assertEquals(0x10, socket.getTrafficClass());
        socket.close();
    }

    @Test
    public void datagramChannel() throws Exception {
        DatagramChannel channel = DatagramChannel.open();
        channel.socket().bind(new InetSocketAddress(0));

        channel.socket().setTrafficClass(0x10);
        channel.send(ByteBuffer.wrap(DATA), new InetSocketAddress(DESTINATION, 5683));
        System.out.println("VERIFY IN WIRESHARK THAT PACKET'S TRAFFIC-CLASS IS SET!");

        assertEquals(0x10, channel.socket().getTrafficClass());
        channel.close();
    }
}
