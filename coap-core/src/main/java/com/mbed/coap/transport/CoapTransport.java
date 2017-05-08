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

import com.mbed.coap.packet.CoapPacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java8.util.concurrent.CompletableFuture;

/**
 * Created by szymon
 */
public interface CoapTransport {
    void start(CoapReceiver coapReceiver) throws IOException;

    void stop();

    CompletableFuture<Boolean> sendPacket(CoapPacket coapPacket, InetSocketAddress adr, TransportContext tranContext);

    InetSocketAddress getLocalSocketAddress();
}
