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
package com.mbed.coap.packet;

public class CoapTcpPacketConverter {

    public static CoapPacket toCoapPacket(CoapRequest request) {
        CoapPacket packet = new CoapPacket(request.getPeerAddress());
        packet.setMessageType(null);

        if (request.isPing()) {
            packet.setCode(Code.C702_PING);
        } else {
            packet.setMethod(request.getMethod());
            packet.setToken(request.getToken());
            packet.setHeaderOptions(request.options());
            packet.setPayload(request.getPayload());
        }

        return packet;
    }

    public static CoapPacket toCoapPacket(SeparateResponse resp) {
        CoapPacket packet = new CoapPacket(resp.getPeerAddress());
        packet.setMessageType(null);
        packet.setCode(resp.getCode());
        packet.setToken(resp.getToken());
        packet.setHeaderOptions(resp.options().duplicate());
        packet.setPayload(resp.getPayload());
        return packet;
    }


}
