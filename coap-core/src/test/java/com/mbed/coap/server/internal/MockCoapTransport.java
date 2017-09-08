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
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by szymon
 */
public class MockCoapTransport extends BlockingCoapTransport {

    private volatile CoapReceiver coapReceiver = null;
    public final BlockingQueue<CoapPacket> sentPackets = new ArrayBlockingQueue<CoapPacket>(100);

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        this.coapReceiver = coapReceiver;
    }

    @Override
    public void stop() {
        coapReceiver = null;
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException {
        sentPackets.add(coapPacket);
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return new InetSocketAddress(5683);
    }

    public void receive(CoapPacket packet) {
        coapReceiver.handle(packet, TransportContext.NULL);
    }
}
