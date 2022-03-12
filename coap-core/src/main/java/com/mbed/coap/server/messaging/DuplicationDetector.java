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
package com.mbed.coap.server.messaging;

import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.PutOnlyMap;

/**
 * Checks if incoming request has been repeated
 */
class DuplicationDetector {
    public static final CoapPacket EMPTY_COAP_PACKET = new CoapPacket(null);

    private final PutOnlyMap<CoapRequestId, CoapPacket> requestMap;

    public DuplicationDetector(PutOnlyMap<CoapRequestId, CoapPacket> cache) {
        this.requestMap = cache;
    }

    public CoapPacket isMessageRepeated(CoapPacket request) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress());

        return requestMap.putIfAbsent(requestId, EMPTY_COAP_PACKET);
    }

    public void putResponse(CoapPacket request, CoapPacket response) {
        CoapRequestId requestId = new CoapRequestId(request.getMessageId(), request.getRemoteAddress());
        requestMap.put(requestId, response);
    }

}
