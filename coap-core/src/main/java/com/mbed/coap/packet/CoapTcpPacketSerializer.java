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
package com.mbed.coap.packet;

import static com.mbed.coap.packet.PacketUtils.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.exception.CoapMessageFormatException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java8.util.Optional;

/**
 * This class implements serialization on de-serialization for CoAP over TCP packet.
 * draft-ietf-core-coap-tcp-tls-09
 */
public final class CoapTcpPacketSerializer {

    /**
     * De-serialize CoAP over TCP message from input stream.
     *
     * @param remoteAddress remote address
     * @param inputStream input stream
     * @return CoapPacket instance
     * @throws IOException   - in case of EOF, closed stream or other low-level errors
     * @throws CoapException - and subclasses in case of CoAP parsing failed.
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, InputStream inputStream) throws IOException, CoapException {
        return deserialize(remoteAddress, inputStream, true);
    }

    /**
     * Returns CoapPacket only if able to deserialize whole packet. Otherwise returns empty Optional.
     * Client is responsible to restore stream position if deserialization failed.
     *
     * @param remoteAddress - remote addres from which packet is received
     * @param inputStream - stream to read data
     * @return CoapPacket wrapped to Optional if able to deserialize or empty Optional otherwise
     * @throws IOException   - in case of EOF, closed stream or other low-level errors
     * @throws CoapException - and subclasses in case of CoAP parsing failed.
     */

    public static Optional<CoapPacket> deserializeIfEnoughData(InetSocketAddress remoteAddress, InputStream inputStream) throws IOException, CoapException {
        try {
            return Optional.of(deserialize(remoteAddress, inputStream, false));
        } catch (NotEnoughDataException ex) {
            return Optional.empty();
        }
    }

    private static CoapPacket deserialize(InetSocketAddress remoteAddress, InputStream inputStream, boolean orBlock) throws IOException, CoapException {
        StrictInputStream is = new StrictInputStream(inputStream);
        CoapPacketParsingContext pktContext = deserializeHeader(remoteAddress, is, orBlock);
        CoapPacket pkt = pktContext.getCoapPacket();

        HeaderOptions options = new HeaderOptions();
        int leftPayloadLen = options.deserialize(is, pkt.getCode(), orBlock, Optional.of((int) pktContext.getLength()));
        pkt.setHeaderOptions(options);

        if (leftPayloadLen > 0) {
            pkt.setPayload(readN(is, leftPayloadLen, orBlock));
        }
        return pkt;
    }

    private static CoapPacketParsingContext deserializeHeader(InetSocketAddress remoteAddress, StrictInputStream is, boolean orBlock) throws IOException, CoapException {

        int len1AndTKL = read8(is, orBlock);

        int len1 = (len1AndTKL >> 4) & 0x0F;
        int tokenLength = len1AndTKL & 0x0F;

        long len = readPacketLen(len1, is, orBlock);

        int codeOrMethod = read8(is, orBlock);

        byte[] token = readToken(is, tokenLength, orBlock);

        CoapPacket coapPacket = new CoapPacket(remoteAddress);

        parseAndSetCodeOrMethod(coapPacket, codeOrMethod);
        coapPacket.setMessageType(null); //override default
        coapPacket.setToken(token);

        return new CoapPacketParsingContext(coapPacket, len);
    }

    private static void parseAndSetCodeOrMethod(CoapPacket coapPacket, int codeOrMethod) throws CoapException {
        if (codeOrMethod >= 1 && codeOrMethod <= 10) {
            //method code
            coapPacket.setMethod(Method.valueOf(codeOrMethod));
        } else {
            coapPacket.setCode(Code.valueOf(codeOrMethod));
        }
    }

    private static long readPacketLen(int len1, StrictInputStream is, boolean orBlock) throws IOException {
        switch (len1) {
            case 15:
                return read32(is, orBlock) + 65805;
            case 14:
                return read16(is, orBlock) + 269;
            case 13:
                return read8(is, orBlock) + 13;

            default:
                return len1;
        }
    }

    private static byte[] readToken(StrictInputStream is, int tokenLength, boolean orBlock) throws IOException, CoapException {
        if (tokenLength == 0) {
            return null;
        }
        if (tokenLength < 0 || tokenLength > 8) {
            throw new CoapMessageFormatException("Token length invalid, should be in range 0..8");
        }

        return readN(is, tokenLength, orBlock);
    }


    private static class CoapPacketParsingContext {
        private final CoapPacket coapPacket;
        private final long packetLength;

        public CoapPacketParsingContext(CoapPacket coapPacket, long packetLength) {
            this.coapPacket = coapPacket;
            this.packetLength = packetLength;
        }

        public CoapPacket getCoapPacket() {
            return coapPacket;
        }

        public long getLength() {
            return packetLength;
        }

    }

    /**
     * Serialize CoAP over TCP message
     *
     * @param coapPacket CoAP packet object
     * @return serialized data
     * @throws CoapException exception if coap packet can not be serialized
     */
    public static byte[] serialize(CoapPacket coapPacket) throws CoapException, IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        writeTo(os, coapPacket);

        return os.toByteArray();
    }


    private static int packetLenCode(int packetLen) {
        if (packetLen < 13) {
            return packetLen;
        } else if (packetLen < 269) {
            return 13;
        } else if (packetLen < 65805) {
            return 14;
        } else {
            return 15;
        }

    }

    private static void writeExtendedPacketLength(OutputStream os, int packetLenCode, int fullPacketLength) throws IOException {
        if (packetLenCode < 13) {
            return;
        }

        switch (packetLenCode) {
            case 13:
                write8(os, fullPacketLength - 13);
                break;
            case 14:
                write16(os, fullPacketLength - 269);
                break;
            case 15:
                write32(os, fullPacketLength - 65805);
                break;
            default:
                // should never happen
                throw new RuntimeException("Unexpected packet len code: " + packetLenCode);
        }
    }


    /**
     * Writes serialized CoAP packet to given OutputStream.
     *
     * @param os output stream
     * @param coapPacket CoAP packet object
     * @throws CoapException serialization exception
     */
    public static void writeTo(OutputStream os, CoapPacket coapPacket) throws CoapException, IOException {

        // we have to serialize options to byteArray to claculate their size
        // because options size included into packet length field together with
        // payload marker and payload size
        ByteArrayOutputStream headerOptionsStream = new ByteArrayOutputStream();
        coapPacket.headers().serialize(headerOptionsStream);

        // token length
        int tokenLen = coapPacket.getToken().length;

        if (tokenLen > 8) {
            throw new CoapException("Token length should not exceed 8 bytes");
        }

        // packet length or extended length code
        int optionsLength = headerOptionsStream.size();
        int payloadLen = coapPacket.getPayload().length;
        int payloadMarkerLen = payloadLen > 0 ? 1 : 0;

        int packetLength = optionsLength + payloadMarkerLen + payloadLen;

        int packetLen1Code = packetLenCode(packetLength);

        //first header byte
        write8(os, (packetLen1Code << 4) | tokenLen);

        //Extended Length
        writeExtendedPacketLength(os, packetLen1Code, packetLength);

        // Code
        CoapPacket.writeCode(os, coapPacket);

        //TKL Bytes
        os.write(coapPacket.getToken());

        //Options
        os.write(headerOptionsStream.toByteArray());

        //Payload
        if (coapPacket.getPayload() != null && coapPacket.getPayload().length > 0) {
            os.write(CoapPacket.PAYLOAD_MARKER);
            os.write(coapPacket.getPayload());
        }

    }


}
