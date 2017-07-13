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
package com.mbed.coap.packet;

import com.mbed.coap.exception.CoapException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * This class implements serialization on de-serialization for CoAP over TCP packet.
 * draft-ietf-core-coap-tcp-tls-09
 */
public final class CoapOverTcp {

    /**
     * De-serialize CoAP over TCP message from input stream.
     *
     * @param remoteAddress remote address
     * @param inputStream input stream
     * @return CoapPacket instance
     * @throws CoapException if input stream can not be de-serialized
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, InputStream inputStream) throws CoapException {
        CoapPacket cp = new CoapPacket(remoteAddress);

        try {
            // Len & TKL
            int tempByte = inputStream.read();
            int plLen = tempByte >>> 4;
            byte tokenLen = (byte) (tempByte & 0x0F);

            //Extended Length
            if (plLen == 13) {
                plLen += inputStream.read();

            } else if (plLen == 14) {
                plLen = inputStream.read() << 8;
                plLen += inputStream.read();
                plLen += 269;

            } else if (plLen == 15) {
                plLen = inputStream.read() << 24;
                plLen += inputStream.read() << 16;
                plLen += inputStream.read() << 8;
                plLen += inputStream.read();
                plLen += 65805;
            }

            // Code
            tempByte = inputStream.read();
            if (tempByte >= 1 && tempByte <= 10) {
                //method code
                cp.setMethod(Method.valueOf(tempByte));
            } else {
                cp.setCode(Code.valueOf(tempByte));
            }
            cp.setMessageType(null); //override default

            //TKL Bytes
            byte[] token = new byte[tokenLen];
            inputStream.read(token);
            cp.setToken(token);

            //Options
            boolean hasPayloadMarker;
            if (cp.getCode() != null && cp.getCode().isSignaling()) {
                SignalingOptions options = new SignalingOptions();
                hasPayloadMarker = options.deserialize(inputStream, cp.getCode());
                cp.setSignalingOptions(options);
            } else {
                HeaderOptions options = new HeaderOptions();
                hasPayloadMarker = options.deserialize(inputStream);
                cp.setHeaderOptions(options);
            }

            //Payload
            if (hasPayloadMarker) {
                byte[] payload = new byte[plLen];
                inputStream.read(payload);
                cp.setPayload(payload);
            }

        } catch (IOException iOException) {
            throw new CoapException(iOException);
        }

        return cp;
    }

    /**
     * Serialize CoAP over TCP message
     *
     * @param coapPacket CoAP packet object
     * @return serialized data
     * @throws CoapException exception if coap packet can not be serialized
     */
    public static byte[] serialize(CoapPacket coapPacket) throws CoapException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        writeTo(os, coapPacket);

        return os.toByteArray();
    }

    /**
     * Writes serialized CoAP packet to given OutputStream.
     *
     * @param os output stream
     * @param coapPacket CoAP packet object
     * @throws CoapException serialization exception
     */
    public static void writeTo(OutputStream os, CoapPacket coapPacket) throws CoapException {
        try {
            // Len & TKL
            int tempByte = coapPacket.getToken().length;
            int plLen = coapPacket.getPayload().length;

            if (plLen < 13) {
                tempByte += (plLen << 4);
            } else if (plLen < 269) {
                tempByte += (13 << 4);
            } else if (plLen < 65805) {
                tempByte += (14 << 4);
            } else {
                tempByte += (15 << 4);
            }

            os.write(tempByte);

            //Extended Length
            writeExtendedLength(os, plLen);

            // Code
            Code code = writeCode(os, coapPacket);

            //TKL Bytes
            os.write(coapPacket.getToken());

            //Options
            if (code != null && code.isSignaling()) {
                coapPacket.signalingOptions().serialize(os);
            } else {
                coapPacket.headers().serialize(os);
            }

            //Payload
            if (coapPacket.getPayload() != null && coapPacket.getPayload().length > 0) {
                os.write(CoapPacket.PAYLOAD_MARKER);
                os.write(coapPacket.getPayload());
            }

        } catch (IOException iOException) {
            throw new CoapException(iOException.getMessage(), iOException);
        }
    }

    private static Code writeCode(OutputStream os, CoapPacket coapPacket) throws CoapException, IOException {
        Code code = coapPacket.getCode();
        Method method = coapPacket.getMethod();

        if (code != null && method != null) {
            throw new CoapException("Forbidden operation: 'code' and 'method' use at a same time");
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

    private static void writeExtendedLength(OutputStream os, int plLen) throws IOException {
        if (plLen >= 13 && plLen < 269) {
            os.write(plLen - 13);
        } else if (plLen >= 269 && plLen < 65805) {
            os.write((0xFF00 & (plLen - 269)) >> 8);
            os.write(0x00FF & (plLen - 269));
        } else if (plLen >= 65805) {
            os.write((0xFF000000 & (plLen - 65805)) >> 24);
            os.write((0x00FF0000 & (plLen - 65805)) >> 16);
            os.write((0x0000FF00 & (plLen - 65805)) >> 8);
            os.write(0x000000FF & (plLen - 65805));
        }
    }

}
