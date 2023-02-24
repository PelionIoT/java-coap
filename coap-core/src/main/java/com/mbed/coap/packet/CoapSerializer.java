/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class CoapSerializer {
    static final int PAYLOAD_MARKER = 0xFF;

    /**
     * Serialize CoAP message
     *
     * @param coapPacket CoAP packet object
     * @return serialized data
     */
    public static byte[] serialize(CoapPacket coapPacket) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        serialize(coapPacket, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Writes serialized CoAP packet to given OutputStream.
     *
     * @param outputStream output stream
     */
    public static void serialize(CoapPacket coap, OutputStream outputStream) {
        try {
            int tempByte;

            tempByte = (0x3 & 1) << 6;                                 // Version
            tempByte |= (0x3 & coap.getMessageType().ordinal()) << 4;  // Transaction Message Type
            tempByte |= coap.getToken().size() & 0xF;                  // Token length

            outputStream.write(tempByte);
            writeCode(outputStream, coap);

            outputStream.write(0xFF & (coap.getMessageId() >> 8));
            outputStream.write(0xFF & coap.getMessageId());

            //token
            coap.getToken().writeTo(outputStream);

            // options
            coap.headers().serialize(outputStream);

            //payload
            if (coap.getPayload().nonEmpty()) {
                outputStream.write(PAYLOAD_MARKER);
                coap.getPayload().writeTo(outputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    public static Code writeCode(OutputStream os, CoapPacket coapPacket) throws IOException {
        Code code = coapPacket.getCode();
        Method method = coapPacket.getMethod();

        if (code != null && method != null) {
            throw new IllegalStateException("Forbidden operation: 'code' and 'method' use at a same time");
        }
        if (code != null) {
            os.write(code.getCoapCode());
        } else if (method != null) {
            os.write(method.getCode());
        } else { //no code or method used
            os.write(0);
        }
        return code;
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param remoteAddress remote address
     * @param rawData data
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, byte[] rawData) throws CoapException {
        return deserialize(remoteAddress, rawData, rawData.length);
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param remoteAddress remote address
     * @param rawData data
     * @param length data length
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, byte[] rawData, int length) throws CoapException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawData, 0, length);
        return deserialize(remoteAddress, inputStream);
    }

    /**
     * De-serialize CoAP message from input stream.
     *
     * @param remoteAddress remote address
     * @param input input stream
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, InputStream input) throws CoapException {
        InputStream inputStream = EofInputStream.wrap(input);

        CoapPacket coap = new CoapPacket(remoteAddress);
        try {
            int tempByte = inputStream.read();      //first byte

            int version = (byte) ((tempByte & 0xC0) >> 6);
            if (version != 1) {
                throw new CoapException("CoAP version %s not supported", version);
            }

            coap.setMessageType(MessageType.valueOf((tempByte >> 4) & 0x3));

            byte tokenLen = (byte) (tempByte & 0x0F);
            if (tokenLen > 8) {
                throw new CoapException("Wrong TOKEN value, size should be within range 0-8");
            }

            tempByte = inputStream.read();         //second byte
            if (tempByte >= 1 && tempByte <= 10) {
                //method code
                coap.setMethod(Method.valueOf(tempByte));
            } else {
                coap.setCode(Code.valueOf(tempByte));
            }

            int messageId = inputStream.read() << 8;
            messageId = messageId | inputStream.read();
            coap.setMessageId(messageId);

            //token
            coap.setToken(Opaque.read(inputStream, tokenLen));

            //read headers
            HeaderOptions options = new HeaderOptions();
            boolean hasPayloadMarker = options.deserialize(inputStream);
            coap.setHeaderOptions(options);

            //read payload
            if (hasPayloadMarker) {
                int plLen = inputStream.available();
                coap.setPayload(Opaque.read(inputStream, plLen));
            }

            return coap;

        } catch (IOException | IllegalArgumentException ex) {
            throw new CoapException(ex);
        }
    }
}
