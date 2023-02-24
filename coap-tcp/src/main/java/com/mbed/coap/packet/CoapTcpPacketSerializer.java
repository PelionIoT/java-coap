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

import static com.mbed.coap.packet.PacketUtils.read16;
import static com.mbed.coap.packet.PacketUtils.read8;
import static com.mbed.coap.utils.Validations.assume;
import com.mbed.coap.exception.CoapException;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;

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
        return deserialize0(remoteAddress, EofInputStream.wrap(inputStream));
    }

    /**
     * Returns CoapPacket only if able to deserialize whole packet. Otherwise returns empty Optional.
     * Client is responsible to restore stream position if deserialization failed.
     *
     * @param remoteAddress - remote addres from which packet is received
     * @param inputStream - stream to read data
     * @return CoapPacket wrapped to Optional if able to deserialize or empty Optional otherwise
     * @throws IOException   - in case of closed stream or other low-level errors
     * @throws CoapException - and subclasses in case of CoAP parsing failed.
     */

    public static Optional<CoapPacket> deserializeIfEnoughData(InetSocketAddress remoteAddress, InputStream inputStream) throws IOException, CoapException {
        try {
            return Optional.of(deserialize0(remoteAddress, EofInputStream.wrap(inputStream)));
        } catch (EOFException ex) {
            return Optional.empty();
        }
    }

    private static CoapPacket deserialize0(InetSocketAddress remoteAddress, EofInputStream is) throws IOException, CoapException {
        CoapPacketParsingContext pktContext = deserializeHeader(remoteAddress, is);
        CoapPacket pkt = pktContext.getCoapPacket();

        HeaderOptions options;
        if (pkt.getCode() != null && pkt.getCode().isSignaling()) {
            options = new SignallingHeaderOptions(pkt.getCode());
        } else {
            options = new HeaderOptions();
        }
        int leftPayloadLen = options.deserialize(is, (int) pktContext.getLength());
        pkt.setHeaderOptions(options);

        if (leftPayloadLen > 0) {
            pkt.setPayload(Opaque.read(is, leftPayloadLen));
        }
        return pkt;
    }

    private static CoapPacketParsingContext deserializeHeader(InetSocketAddress remoteAddress, EofInputStream is) throws IOException, CoapException {

        int len1AndTKL = read8(is);

        int len1 = (len1AndTKL >> 4) & 0x0F;
        int tokenLength = len1AndTKL & 0x0F;

        long len = readPacketLen(len1, is);

        int codeOrMethod = read8(is);

        Opaque token = readToken(is, tokenLength);

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

    private static long readPacketLen(int len1, EofInputStream is) throws IOException {
        switch (len1) {
            case 15:
                return read32(is) + 65805;
            case 14:
                return read16(is) + 269;
            case 13:
                return read8(is) + 13;

            default:
                return len1;
        }
    }

    private static Opaque readToken(EofInputStream is, int tokenLength) throws IOException {
        assume(tokenLength <= 8, "Token length invalid, should be in range 0..8");
        return Opaque.read(is, tokenLength);
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
        int tokenLen = coapPacket.getToken().size();

        assume(tokenLen <= 8, "Token length should not exceed 8 bytes");

        // packet length or extended length code
        int optionsLength = headerOptionsStream.size();
        int payloadLen = coapPacket.getPayload().size();
        int payloadMarkerLen = payloadLen > 0 ? 1 : 0;

        int packetLength = optionsLength + payloadMarkerLen + payloadLen;

        int packetLen1Code = packetLenCode(packetLength);

        //first header byte
        write8(os, (packetLen1Code << 4) | tokenLen);

        //Extended Length
        writeExtendedPacketLength(os, packetLen1Code, packetLength);

        // Code
        CoapSerializer.writeCode(os, coapPacket);

        //TKL Bytes
        coapPacket.getToken().writeTo(os);

        //Options
        os.write(headerOptionsStream.toByteArray());

        //Payload
        if (coapPacket.getPayload().size() > 0) {
            os.write(CoapSerializer.PAYLOAD_MARKER);
            coapPacket.getPayload().writeTo(os);
        }

    }

    static long read32(InputStream is) throws IOException {
        long ret = is.read() << 24;
        ret |= is.read() << 16;
        ret |= is.read() << 8;
        ret |= is.read();

        return ret;
    }

    static void write8(OutputStream os, int data) throws IOException {
        os.write(data);
    }

    static void write16(OutputStream os, int data) throws IOException {
        os.write((data >> 8) & 0xFF);
        os.write((data >> 0) & 0xFF);
    }

    static void write32(OutputStream os, long data) throws IOException {
        os.write((int) ((data >> 24) & 0xFF));
        os.write((int) ((data >> 16) & 0xFF));
        os.write((int) ((data >> 8) & 0xFF));
        os.write((int) ((data >> 0) & 0xFF));
    }
}
