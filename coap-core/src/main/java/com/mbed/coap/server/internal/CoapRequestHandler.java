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

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.TransportContext;

/**
 * Created by olesmi01 on 15.08.2017.
 * CoAP requests handler interface which is called by CoAP protocol servers impl (messaging layer)
 */
public interface CoapRequestHandler {
    void handleRequest(CoapPacket request, TransportContext transportContext);

    boolean handleObservation(CoapPacket packet, TransportContext context);
}
