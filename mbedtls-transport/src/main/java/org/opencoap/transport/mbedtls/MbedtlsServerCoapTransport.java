/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package org.opencoap.transport.mbedtls;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.CoapTransport;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.opencoap.ssl.transport.DtlsServer;

public class MbedtlsServerCoapTransport implements CoapTransport {
    private final DtlsServer dtlsServer;

    public MbedtlsServerCoapTransport(DtlsServer dtlsServer) {
        this.dtlsServer = dtlsServer;
    }

    @Override
    public void start(CoapReceiver receiver) {
        dtlsServer.listen((srcAddress, bytes) -> {
            CoapPacket coapPacket = CoapPacket.read(srcAddress, bytes);
            receiver.handle(coapPacket);
        });
    }

    @Override
    public void stop() {
        dtlsServer.close();
    }

    @Override
    public CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket) {
        return dtlsServer.send(coapPacket.toByteArray(), coapPacket.getRemoteAddress());
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return dtlsServer.getLocalAddress();
    }
}
