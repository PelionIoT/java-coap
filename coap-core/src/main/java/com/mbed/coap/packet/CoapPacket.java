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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.utils.HexArray;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java8.util.Objects;

/**
 * This class encode and decode CoAP messages based on RFC 7252 document
 *
 * @author szymon
 */
public class CoapPacket implements Serializable {

    static final int PAYLOAD_MARKER = 0xFF;
    public static final byte[] DEFAULT_TOKEN = new byte[]{};
    private byte version = 1;
    private MessageType messageType = MessageType.Confirmable;
    private int messageId;
    private Code code;
    private Method method;
    private byte[] payload = new byte[0];
    private final InetSocketAddress remoteAddress;
    private HeaderOptions options = new HeaderOptions();
    private byte[] token = DEFAULT_TOKEN; //opaque

    /**
     * CoAP packet constructor.
     *
     * @param remoteAddress remote address
     */
    public CoapPacket(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /**
     * Constructs CoAP response.
     *
     * @param code response code
     * @param messageType message type
     * @param otherEndAddress destination address
     */
    public CoapPacket(Code code, MessageType messageType, InetSocketAddress otherEndAddress) {
        this.code = code;
        this.messageType = messageType;
        this.remoteAddress = otherEndAddress;
    }

    /**
     * Constructs CoAP request.
     *
     * @param method request method
     * @param messageType message type
     * @param uriPath uri path
     * @param remoteAddress destination address
     */
    public CoapPacket(Method method, MessageType messageType, String uriPath, InetSocketAddress remoteAddress) {
        this.method = method;
        this.messageType = messageType;
        this.headers().setUriPath(uriPath);
        this.remoteAddress = remoteAddress;
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
    public static CoapPacket read(InetSocketAddress remoteAddress, byte[] rawData, int length) throws CoapException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawData, 0, length);
        CoapPacket cp = new CoapPacket(remoteAddress);
        cp.readFrom(inputStream);
        return cp;
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param remoteAddress remote address
     * @param rawData data
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket read(InetSocketAddress remoteAddress, byte[] rawData) throws CoapException {
        return read(remoteAddress, rawData, rawData.length);
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * De-serialize CoAP message from input stream.
     *
     * @param remoteAddress remote address
     * @param inputStream input stream
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket deserialize(InetSocketAddress remoteAddress, InputStream inputStream) throws CoapException {
        CoapPacket coapPacket = new CoapPacket(remoteAddress);
        coapPacket.readFrom(inputStream);
        return coapPacket;
    }

    /**
     * Serialize CoAP message
     *
     * @param coapPacket CoAP packet object
     * @return serialized data
     * @throws CoapException exception if coap packet can not be serialized
     */
    public static byte[] serialize(CoapPacket coapPacket) throws CoapException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        coapPacket.writeTo(outputStream);
        return outputStream.toByteArray();
    }

    protected void readFrom(InputStream inputStream) throws CoapException {
        try {
            int tempByte = inputStream.read();      //first byte

            version = (byte) ((tempByte & 0xC0) >> 6);
            if (version != 1) {
                throw new CoapException("CoAP version %s not supported", version);
            }

            messageType = MessageType.valueOf((tempByte >> 4) & 0x3);

            byte tokenLen = (byte) (tempByte & 0x0F);
            if (tokenLen > 8) {
                throw new CoapException("Wrong TOKEN value, size should be within range 0-8");
            }

            tempByte = inputStream.read();         //second byte
            if (tempByte >= 1 && tempByte <= 10) {
                //method code
                method = Method.valueOf(tempByte);
            } else {
                code = Code.valueOf(tempByte);
            }

            messageId = inputStream.read() << 8;
            messageId = messageId | inputStream.read();

            //token
            token = new byte[tokenLen];
            inputStream.read(token);

            //read headers
            options = new HeaderOptions();
            boolean hasPayloadMarker = options.deserialize(inputStream, code);

            //read payload
            if (hasPayloadMarker) {
                int plLen = inputStream.available();
                this.payload = new byte[plLen];
                inputStream.read(payload);
            }

        } catch (IOException | IllegalArgumentException ex) {
            throw new CoapException(ex);
        }
    }

    /**
     * Returns CoAP header options instance.
     *
     * @return header options instance
     */
    public final HeaderOptions headers() {
        return options;
    }

    public void setHeaderOptions(HeaderOptions options) {
        this.options = options;
    }

    /**
     * Creates response CoapPacket object setting default response parameters.
     *
     * @return response CoapPacket object
     */
    public CoapPacket createResponse() {
        if (this.getCode() == null) {
            return createResponse(Code.C205_CONTENT);
        } else {
            return createResponse(null);
        }
    }

    /**
     * Creates response CoapPacket object setting default response parameters.
     *
     * @param responseCode response code
     * @return response CoapPacket object
     */
    public CoapPacket createResponse(Code responseCode) {
        if (messageType == MessageType.NonConfirmable) {
            CoapPacket response = new CoapPacket(this.getRemoteAddress());
            response.setMessageType(MessageType.NonConfirmable);
            response.setCode(responseCode);
            response.setToken(getToken());
            return response;
        }
        if (messageType == MessageType.Confirmable) {
            CoapPacket response = new CoapPacket(this.getRemoteAddress());
            response.setMessageId(this.messageId);
            response.setMessageType(MessageType.Acknowledgement);
            response.setCode(responseCode);
            if (responseCode != null) {
                response.setToken(getToken());
                //do not put token into empty-ack
            }
            return response;
        }
        if (messageType == null && method != null) {
            CoapPacket response = new CoapPacket(this.getRemoteAddress());
            response.setMessageId(this.messageId);
            response.setToken(getToken());
            response.setCode(responseCode);
            return response;
        }

        return null;
    }

    /**
     * Returns CoAP version.
     *
     * @return version
     */
    public byte getVersion() {
        return version;
    }

    /**
     * Returns CoAP message type.
     *
     * @return message type
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Sets CoAP message type.
     *
     * @param messageType message type
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns method.
     *
     * @return method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Sets CoAP method.
     *
     * @param method method
     */
    public void setMethod(Method method) {
        this.method = method;
    }

    /**
     * Sets message identification.
     *
     * @param messageID message ID
     */
    public synchronized void setMessageId(int messageID) {
        if (messageID > 65535 || messageID < 0) {
            throw new IllegalArgumentException("invalid messageid, should be within range 0-65535: " + messageID);
        }

        this.messageId = messageID;
    }

    /**
     * Returns message identification.
     *
     * @return message ID
     */
    public synchronized int getMessageId() {
        return messageId;
    }

    /**
     * Return payload as byte array.
     *
     * @return payload
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Returns payload as String.
     *
     * @return payload as String or null if payload is empty
     */
    public String getPayloadString() {
        if (payload.length > 0) {
            return DataConvertingUtility.decodeToString(payload);
        }
        return null;
    }

    /**
     * Sets CoAP payload data as String.
     *
     * @param payload payload
     */
    public void setPayload(String payload) {
        setPayload(DataConvertingUtility.encodeString(payload));
    }

    /**
     * Sets CoAP payload data as byte array.
     *
     * @param payload payload
     */
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public void setToken(byte[] token) {
        if (token != null && token.length > 8) {
            throw new IllegalArgumentException("Wrong TOKEN value, size should be within range 0-8");
        }
        if (token == null || token.length == 0) {
            this.token = DEFAULT_TOKEN;
        } else {
            this.token = token;
        }
    }

    public byte[] getToken() {
        return token;
    }

    /**
     * Writes serialized CoAP packet to given OutputStream.
     *
     * @param outputStream output stream
     * @throws CoapException serialization exception
     */
    public void writeTo(OutputStream outputStream) {
        try {
            int tempByte;

            tempByte = (0x3 & version) << 6;            //Version
            tempByte |= (0x3 & messageType.ordinal()) << 4;  //Transaction Message Type
            tempByte |= token.length & 0xF;                  //Token length

            outputStream.write(tempByte);
            writeCode(outputStream, this);

            outputStream.write(0xFF & (messageId >> 8));
            outputStream.write(0xFF & messageId);

            //token
            outputStream.write(token);

            // options
            options.serialize(outputStream);

            //payload
            if (payload != null && payload.length > 0) {
                outputStream.write(PAYLOAD_MARKER);
                outputStream.write(payload);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    static Code writeCode(OutputStream os, CoapPacket coapPacket) throws IOException {
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
     * Creates a CoAP packet and Returns array of bytes.
     *
     * @return serialized CoAP packet
     * @throws CoapException serialization exception
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeTo(outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Returns CoAP code.
     *
     * @return code value or null
     */
    public Code getCode() {
        return code;
    }

    /**
     * Sets CoAP code.
     *
     * @param code CoAP code
     */
    public void setCode(Code code) {
        this.code = code;
    }

    /**
     * Indicates if this CoAP packet must be acknowledged.
     *
     * @return true if this message must be acknowledged
     */
    public boolean getMustAcknowledge() {
        return this.messageType == MessageType.Confirmable;
    }

    @Override
    public String toString() {
        return toString(false, false, true);
    }

    public String toString(boolean printFullPayload) {
        return toString(printFullPayload, false, true);
    }

    public String toString(boolean printFullPayload, boolean printPayloadOnlyAsHex, boolean printAddress) {
        return toString(printFullPayload, printPayloadOnlyAsHex, printAddress, false);
    }

    public String toString(boolean printFullPayload, boolean printPayloadOnlyAsHex, boolean printAddress, boolean doNotPrintPayload) {
        StringBuilder sb = new StringBuilder();

        if (printAddress && this.getRemoteAddress() != null) {
            sb.append(this.getRemoteAddress()).append(' ');
        }

        if (messageType != null) {
            sb.append(getMessageType().toString());
        }
        if (method != null) {
            sb.append(' ').append(method.toString());
        }
        if (code != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(code.codeToString());
        }

        sb.append(" MID:").append(this.messageId);

        if (this.token.length > 0) {
            sb.append(" Token:0x").append(HexArray.toHex(this.token));
        }

        options.toString(sb, code);

        payloadToString(printFullPayload, printPayloadOnlyAsHex, doNotPrintPayload, sb);

        return sb.toString();
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private void payloadToString(boolean printFullPayload, boolean printPayloadOnlyAsHex, boolean doNotPrintPayload, StringBuilder sb) {
        if (payload != null && payload.length > 0) {
            if (doNotPrintPayload) {
                sb.append(" pl(").append(payload.length).append(')');
            } else {
                payloadToString(printFullPayload, sb, printPayloadOnlyAsHex);
            }
        }
    }

    private void payloadToString(boolean printFullPayload, StringBuilder sb, boolean printPayloadOnlyAsHex) {
        if (!printPayloadOnlyAsHex && isTextBasedContentFormat()) {
            String decodedPayload = getPayloadString();
            if (decodedPayload.length() < 46 || printFullPayload) {
                sb.append(" pl:'").append(decodedPayload).append('\'');
            } else {
                sb.append(" pl(").append(payload.length).append("):'").append(decodedPayload.substring(0, 44)).append("..'");
            }
        } else {
            if (!printFullPayload) {
                sb.append(" pl(").append(payload.length).append("):0x").append(HexArray.toHexShort(payload, 22));
            } else {
                sb.append(" pl(").append(payload.length).append("):0x").append(HexArray.toHex(payload));
            }
        }
    }

    private boolean isTextBasedContentFormat() {
        if (headers().getContentFormat() == null) {
            return false;
        }
        return headers().getContentFormat() == MediaTypes.CT_TEXT_PLAIN
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_JSON
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_LINK__FORMAT
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_XML
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_LWM2M_JSON;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + this.version;
        hash = 41 * hash + Objects.hashCode(this.messageType);
        hash = 41 * hash + this.messageId;
        hash = 41 * hash + Objects.hashCode(this.code);
        hash = 41 * hash + Objects.hashCode(this.method);
        hash = 41 * hash + Arrays.hashCode(this.payload);
        hash = 41 * hash + Objects.hashCode(this.remoteAddress);
        hash = 41 * hash + Objects.hashCode(this.options);
        hash = 41 * hash + Arrays.hashCode(this.token);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CoapPacket other = (CoapPacket) obj;
        if (this.version != other.version) {
            return false;
        }
        if (this.messageType != other.messageType) {
            return false;
        }
        if (this.messageId != other.messageId) {
            return false;
        }
        if (this.code != other.code) {
            return false;
        }
        if (this.method != other.method) {
            return false;
        }
        if (!Arrays.equals(this.payload, other.payload)) {
            return false;
        }
        if (!Objects.equals(this.remoteAddress, other.remoteAddress)) {
            return false;
        }
        if (!Objects.equals(this.options, other.options)) {
            return false;
        }
        if (!Arrays.equals(this.token, other.token)) {
            return false;
        }
        return true;
    }

}
