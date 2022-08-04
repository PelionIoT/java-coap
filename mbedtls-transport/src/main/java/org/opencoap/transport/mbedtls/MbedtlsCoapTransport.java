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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.transport.TransportExecutors;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.opencoap.ssl.transport.DtlsTransmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MbedtlsCoapTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(MbedtlsCoapTransport.class);
    private final Executor readingWorker = TransportExecutors.newWorker("mbedtls-client");

    private final InetSocketAddress destAdr;
    private final DtlsTransmitter transmitter;

    public MbedtlsCoapTransport(DtlsTransmitter transmitter) {
        this.transmitter = transmitter;
        this.destAdr = transmitter.getRemoteAddress();
    }

    @Override
    public void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        transmitter.send(coapPacket.toByteArray());
    }

    @Override
    public void start(CoapReceiver receiver) {
        TransportExecutors.loop(readingWorker, () -> {
            try {
                byte[] buf = transmitter.receive(Duration.ofSeconds(30));
                if (buf.length > 0) {
                    CoapPacket coap = CoapPacket.read(destAdr, buf);
                    receiver.handle(coap, TransportContext.EMPTY);
                }
            } catch (CoapException e) {
                LOGGER.warn("Can not parse coap packet: " + e.getMessage());
            } catch (ClosedSelectorException ex) {
                return false;
            }

            return true;
        });
    }

    @Override
    public void stop() {
        TransportExecutors.shutdown(readingWorker);
        transmitter.close();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return destAdr;
    }
}
