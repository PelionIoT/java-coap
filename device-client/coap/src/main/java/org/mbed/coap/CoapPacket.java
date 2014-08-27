/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.utils.ByteArrayBackedInputStream;
import org.mbed.coap.utils.ByteArrayBackedOutputStream;
import org.mbed.coap.utils.HexArray;

/**
 * This class encode and decode CoAP messages based on RFC 7252 document
 *
 * @author szymon
 */
@SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
public class CoapPacket implements CoapMessage, Serializable {

    static final int PAYLOAD_MARKER = 0xFF;
    public static final byte[] DEFAULT_TOKEN = new byte[]{};
    private byte version = 1;
    private MessageType messageType = MessageType.Confirmable;
    private int messageId;
    private Code code;
    private Method method;
    private byte[] payload = new byte[0];
    private InetSocketAddress otherEndAddress;
    private ExHeaderOptions options = new ExHeaderOptions();
    private byte[] token = DEFAULT_TOKEN; //opaque

    /**
     * CoAP packet constructor.
     */
    public CoapPacket() {
        // nothing to initialize
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
        this.otherEndAddress = otherEndAddress;
    }

    /**
     * Constructs CoAP request.
     *
     * @param method request method
     * @param messageType message type
     * @param uriPath uri path
     * @param otherEndAddress destination address
     */
    public CoapPacket(Method method, MessageType messageType, String uriPath, InetSocketAddress otherEndAddress) {
        this.method = method;
        this.messageType = messageType;
        this.headers().setUriPath(uriPath);
        this.otherEndAddress = otherEndAddress;
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param rawData data
     * @param length data length
     * @param otherEndAddress source address
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket read(byte[] rawData, int length, InetSocketAddress otherEndAddress) throws CoapException {
        ByteArrayBackedInputStream inputStream = new ByteArrayBackedInputStream(rawData, 0, length);
        CoapPacket cp = new CoapPacket();
        cp.readFrom(inputStream);
        cp.otherEndAddress = otherEndAddress;
        return cp;
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param rawData data
     * @param length data length
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket read(byte[] rawData, int length) throws CoapException {
        ByteArrayBackedInputStream inputStream = new ByteArrayBackedInputStream(rawData, 0, length);
        CoapPacket cp = new CoapPacket();
        cp.readFrom(inputStream);
        return cp;
    }

    /**
     * Reads CoAP packet from raw data.
     *
     * @param rawData data
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket read(byte[] rawData) throws CoapException {
        return read(rawData, rawData.length);
    }

    public InetSocketAddress getOtherEndAddress() {
        return otherEndAddress;
    }

    public void setOtherEndAddress(InetSocketAddress otherEndAddress) {
        this.otherEndAddress = otherEndAddress;
    }

    /**
     * De-serialize CoAP message from input stream.
     *
     * @param inputStream input stream
     * @return CoapPacket instance
     * @throws CoapException if can not parse
     */
    public static CoapPacket deserialize(InputStream inputStream) throws CoapException {
        CoapPacket coapPacket = new CoapPacket();
        coapPacket.readFrom(inputStream);
        return coapPacket;
    }

    /**
     * Serialize CoAP message
     *
     * @param coapPacket CoAP packet object
     * @return serialized data
     * @throws CoapException
     */
    public static byte[] serialize(CoapPacket coapPacket) throws CoapException {
        ByteArrayBackedOutputStream outputStream = new ByteArrayBackedOutputStream();

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
            options = new ExHeaderOptions();
            boolean hasPayloadMarker = options.deserialize(inputStream);

            //read payload
            if (hasPayloadMarker) {
                int plLen = inputStream.available();
                this.payload = new byte[plLen];
                inputStream.read(payload);
            }

        } catch (IOException iOException) {
            throw new CoapException(iOException);
        }
    }

    /**
     * Returns header options
     */
    @Override
    public final ExHeaderOptions headers() {
        return options;
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
            CoapPacket response = new CoapPacket();
            response.setMessageType(MessageType.NonConfirmable);
            response.setCode(responseCode);
            response.setToken(getToken());
            response.setOtherEndAddress(this.getOtherEndAddress());
            return response;
        }
        if (messageType == MessageType.Confirmable) {
            CoapPacket response = new CoapPacket();
            response.setMessageId(this.messageId);
            response.setMessageType(MessageType.Acknowledgement);
            response.setCode(responseCode);
            response.setToken(getToken());
            response.setOtherEndAddress(this.getOtherEndAddress());
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

    @Override
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

    @Override
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

    @Override
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String getPayloadString() {
        if (payload.length > 0) {
            return CoapUtils.decodeString(payload);
        }
        return null;
    }

    /**
     * Sets CoAP payload data as String.
     *
     * @param payload payload
     */
    public void setPayload(String payload) {
        setPayload(CoapUtils.encodeString(payload));
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
        if (token == null) {
            this.token = DEFAULT_TOKEN;
        } else {
            this.token = token;
        }
    }

    @Override
    public byte[] getToken() {
        return token;
    }

    /**
     * Writes serialized CoAP packet to given OutputStream.
     *
     * @param outputStream output stream
     * @throws CoapException
     */
    public void writeTo(OutputStream outputStream) throws CoapException {
        try {
            int tempByte;

            tempByte = (0x3 & version) << 6;            //Version
            tempByte |= (0x3 & messageType.ordinal()) << 4;  //Transaction Message Type
            tempByte |= token.length & 0xF;                  //Token length

            outputStream.write(tempByte);
            if (code != null && method != null) {
                throw new CoapException("Forbidden operation: 'code' and 'method' use at a same time");
            }
            if (code != null) {
                outputStream.write(code.getCoapCode());
            } else if (method != null) {
                outputStream.write(method.getCode());
            } else { //no code or method used
                outputStream.write(0);
            }

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
        } catch (IOException iOException) {
            throw new CoapException(iOException.getMessage(), iOException);
        }
    }

    /**
     * Creates a CoAP packet and Returns array of bytes.
     *
     * @return serialized CoAP packet
     * @throws CoapException
     */
    public byte[] toByteArray() throws CoapException {
        ByteArrayBackedOutputStream outputStream = new ByteArrayBackedOutputStream();
        writeTo(outputStream);
        return outputStream.toByteArray();
    }

    @Override
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
    public boolean getMustAcknowladge() {
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
        StringBuilder sb = new StringBuilder();

        if (printAddress && this.getOtherEndAddress() != null) {
            sb.append(this.getOtherEndAddress()).append(' ');
        }

        sb.append(getMessageType().toString());
        if (method != null) {
            sb.append(' ').append(method.toString());
        }
        if (code != null) {
            sb.append(' ').append(code.getHttpCode());
        }
        sb.append(" MID:").append(this.messageId);
        if (this.token != null && this.token.length > 0) {
            sb.append(" Token:0x").append(HexArray.toHex(this.token));
        }

        options.toString(sb);

        if (payload != null && payload.length > 0) {
            payloadToString(printFullPayload, sb, printPayloadOnlyAsHex);
        }

        return sb.toString();
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
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_SENML__JSON
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_XML
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_LWM2M_JSON
                || headers().getContentFormat() == MediaTypes.CT_APPLICATION_LWM2M_TEXT;
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
        hash = 41 * hash + Objects.hashCode(this.otherEndAddress);
        hash = 41 * hash + Objects.hashCode(this.options);
        hash = 41 * hash + Arrays.hashCode(this.token);
        return hash;
    }

    @Override
    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
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
        if (!Objects.equals(this.otherEndAddress, other.otherEndAddress)) {
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
