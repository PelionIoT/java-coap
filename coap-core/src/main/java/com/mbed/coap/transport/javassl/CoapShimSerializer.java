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
package com.mbed.coap.transport.javassl;

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class CoapShimSerializer implements CoapSerializer {

    private final int maxSize;

    public CoapShimSerializer(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void serialize(OutputStream outputStream, CoapPacket coapPacket) throws IOException {
        byte[] encodedCoap = coapPacket.toByteArray();

        DataOutputStream os = new DataOutputStream(outputStream);
        os.writeInt(encodedCoap.length);

        outputStream.write(encodedCoap);
    }

    @Override
    public CoapPacket deserialize(InputStream inputStream, InetSocketAddress sourceAddress) throws CoapException, IOException {
        DataInputStream dis = new DataInputStream(inputStream);
        int len = dis.readInt();
        if (len > maxSize) {
            throw new IOException("Too large or malformed shim packet (" + len + ")");
        }
        byte[] buffer = new byte[len];
        dis.readFully(buffer);

        return CoapPacket.read(sourceAddress, buffer);
    }

}
