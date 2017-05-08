/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.transport;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java8.util.concurrent.CompletableFuture;

/**
 * Created by szymon
 */
public abstract class BlockingCoapTransport implements CoapTransport {

    @Override
    public final CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) {
        CompletableFuture<Boolean> objectCompletableFuture = new CompletableFuture<>();

        try {
            sendPacket0(coapPacket, adr, tranContext);
            objectCompletableFuture.complete(true);
        } catch (Exception ex) {
            objectCompletableFuture.completeExceptionally(ex);
        }

        return objectCompletableFuture;
    }

    public abstract void sendPacket0(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext) throws CoapException, IOException;

}
