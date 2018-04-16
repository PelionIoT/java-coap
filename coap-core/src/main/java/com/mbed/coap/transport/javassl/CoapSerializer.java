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
package com.mbed.coap.transport.javassl;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapTcpPacketSerializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public interface CoapSerializer {

    CoapSerializer UDP = new CoapSerializer() {
        @Override
        public void serialize(OutputStream outputStream, CoapPacket coapPacket) {
            coapPacket.writeTo(outputStream);
        }

        @Override
        public CoapPacket deserialize(InputStream inputStream, InetSocketAddress sourceAddress) throws CoapException {
            return CoapPacket.deserialize(sourceAddress, inputStream);
        }
    };

    CoapSerializer TCP = new CoapSerializer() {
        @Override
        public void serialize(OutputStream outputStream, CoapPacket coapPacket) throws CoapException, IOException {
            CoapTcpPacketSerializer.writeTo(outputStream, coapPacket);
        }

        @Override
        public CoapPacket deserialize(InputStream inputStream, InetSocketAddress sourceAddress) throws CoapException, IOException {
            return CoapTcpPacketSerializer.deserialize(sourceAddress, inputStream);
        }
    };

    void serialize(OutputStream outputStream, CoapPacket coapPacket) throws CoapException, IOException;

    CoapPacket deserialize(InputStream inputStream, InetSocketAddress sourceAddress) throws CoapException, IOException;
}
