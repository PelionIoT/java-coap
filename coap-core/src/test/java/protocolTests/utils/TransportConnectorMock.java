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
package protocolTests.utils;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.utils.AsyncQueue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


public class TransportConnectorMock extends BlockingCoapTransport {

    private final Map<CoapPacket, CoapPacket[]> conversationMap = new LinkedHashMap<>(); //order is important
    private final Map<CoapPacket, IOException> conversationMapToException = new LinkedHashMap<>(); //order is important
    private final AsyncQueue<CoapPacket> receiveQueue = new AsyncQueue<>();
    private CoapPacket lastOutgoingMessage = null;

    @Override
    public void start() throws IOException {
        // do nothing
    }

    @Override
    public void stop() {
        receiveQueue.removeAll();
    }

    @Override
    public void sendPacket0(CoapPacket request) throws IOException {
        CoapPacket[] resp = findResponse(request);
        if (resp != null) {
            for (CoapPacket r : resp) {
                if (r != null) {
                    receive(r, request.getRemoteAddress());
                }
            }
        }
        lastOutgoingMessage = request;
    }

    public void receive(CoapPacket coapPacket) throws InterruptedException {
        receiveQueue.add(coapPacket);
    }

    public void receive(CoapPacket coapPacket, InetSocketAddress sourceAddress) {
        try {
            receive(CoapPacket.read(sourceAddress, coapPacket.toByteArray()));
        } catch (CoapException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public CompletableFuture<CoapPacket> receive() {
        return receiveQueue.poll();
    }

    public CoapPacket getLastOutgoingMessage() {
        return lastOutgoingMessage;
    }

    private CoapPacket[] findResponse(CoapPacket coapPacket) throws IOException {
        //remove address
        try {
            byte[] bytes = coapPacket.toByteArray();
            coapPacket = CoapPacket.read(null, bytes, bytes.length);
        } catch (CoapException e) {
            throw new RuntimeException(e);
        }

        Optional<CoapPacket> first = conversationMap.keySet().stream().findFirst();
        if (!first.isPresent() || !first.get().equals(coapPacket)) {

            Optional<CoapPacket> firstException = conversationMapToException.keySet().stream().findFirst();
            if (!firstException.isPresent() || !firstException.get().equals(coapPacket)) {
                return null;
            }
            throw conversationMapToException.remove(firstException.get());
        }
        return conversationMap.remove(first.get());
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return null;
    }

    public TransportConnectorMockTeacher when(CoapPacket incomingPacket) {
        return new TransportConnectorMockTeacher(incomingPacket);
    }

    public TransportConnectorMockTeacher when(CoapPacketBuilder incomingPacket) {
        return when(incomingPacket.build());
    }

    public class TransportConnectorMockTeacher {
        private final CoapPacket incomingPacket;

        public TransportConnectorMockTeacher(CoapPacket incomingPacket) {
            this.incomingPacket = incomingPacket;
        }

        public void thenNothing() {
            conversationMap.put(incomingPacket, null);
        }

        public void thenThrow(IOException exception) {
            conversationMapToException.put(incomingPacket, exception);
        }

        public void then(CoapPacket responsePacket) {
            conversationMap.put(incomingPacket, new CoapPacket[]{responsePacket});
        }

        public void then(CoapPacketBuilder responsePacket) {
            then(responsePacket.build());
        }

        public void then(CoapPacket responsePacket1, CoapPacket responsePacket2) {
            conversationMap.put(incomingPacket, new CoapPacket[]{responsePacket1, responsePacket2});
        }
    }
}
