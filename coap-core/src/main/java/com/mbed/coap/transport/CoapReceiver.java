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
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CoapReceiver {
    Logger LOGGER = LoggerFactory.getLogger(CoapReceiver.class);

    void handle(CoapPacket packet);

    void onDisconnected(InetSocketAddress remoteAddress);

    void onConnected(InetSocketAddress remoteAddress);

    void start();

    void stop();

    static void logReceived(CoapPacket packet) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("CoAP received [{}]", packet.toString(true));
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[{}] CoAP received [{}]", packet.getRemoteAddrString(), packet.toString(false));
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[{}] CoAP received [{}]", packet.getRemoteAddrString(), packet.toString(false, false, false, true));
        }
    }
}
