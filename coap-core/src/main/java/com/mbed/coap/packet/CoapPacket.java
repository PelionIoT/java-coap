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

import static com.mbed.coap.transport.TransportContext.NON_CONFIRMABLE;
import com.mbed.coap.transport.TransportContext;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * This class encode and decode CoAP messages based on RFC 7252 document
 */
public class CoapPacket {

    private MessageType messageType = MessageType.Confirmable;
    private int messageId;
    private Code code;
    private Method method;
    private Opaque payload = Opaque.EMPTY;
    private final InetSocketAddress remoteAddress;
    private HeaderOptions options = new HeaderOptions();
    private Opaque token = Opaque.EMPTY;
    private TransportContext transportContext = TransportContext.EMPTY;

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

    public static CoapPacket from(CoapRequest req) {
        CoapPacket packet = new CoapPacket(Objects.requireNonNull(req.getPeerAddress()));
        packet.setMessageType(req.getTransContext().get(NON_CONFIRMABLE) ? MessageType.NonConfirmable : MessageType.Confirmable);
        packet.setMethod(req.getMethod());
        packet.setTransportContext(req.getTransContext());
        packet.setToken(req.getToken());
        packet.setHeaderOptions(req.options());
        packet.setPayload(req.getPayload());

        return packet;
    }

    public static CoapPacket from(SeparateResponse resp) {
        MessageType messageType = resp.getTransContext().get(NON_CONFIRMABLE) ? MessageType.NonConfirmable : MessageType.Confirmable;
        CoapPacket packet = new CoapPacket(resp.getCode(), messageType, resp.getPeerAddress());
        packet.setTransportContext(resp.getTransContext());
        packet.setToken(resp.getToken());
        packet.setHeaderOptions(resp.options().duplicate());
        packet.setPayload(resp.getPayload());

        return packet;
    }

    public CoapResponse toCoapResponse() {
        return new CoapResponse(code, payload, options);
    }

    public SeparateResponse toSeparateResponse() {
        return toCoapResponse().toSeparate(getToken(), getRemoteAddress(), getTransportContext());
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public String getRemoteAddrString() {
        if (remoteAddress != null) {
            // to ignore scope_id in case of ipv6
            return remoteAddress.getHostString().split("%")[0].concat(":" + remoteAddress.getPort());
        } else {
            return null;
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

    public CoapPacket createResponseFrom(CoapResponse coapResponse) {
        CoapPacket response = new CoapPacket(this.getRemoteAddress());
        response.setTransportContext(this.transportContext);
        response.setCode(coapResponse.getCode());
        response.setToken(getToken());
        response.setPayload(coapResponse.getPayload());
        response.setHeaderOptions(coapResponse.options().duplicate());

        if (messageType == MessageType.NonConfirmable) {
            response.setMessageType(MessageType.NonConfirmable);
        } else if (messageType == MessageType.Confirmable) {
            response.setMessageId(this.messageId);
            response.setMessageType(MessageType.Acknowledgement);
        } else if (messageType == null && method != null) {
            response.setMessageId(this.messageId);
        }
        return response;
    }

    public CoapRequest toCoapRequest() {
        TransportContext transCtx = (messageType == MessageType.NonConfirmable) ? transportContext.with(NON_CONFIRMABLE, true) : transportContext;

        return new CoapRequest(method, token, options, payload, remoteAddress, transCtx);
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
    public void setMessageId(int messageID) {
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
    public int getMessageId() {
        return messageId;
    }

    /**
     * Return payload as byte array.
     *
     * @return payload
     */
    public Opaque getPayload() {
        return payload;
    }

    /**
     * Returns payload as String.
     *
     * @return payload as String or null if payload is empty
     */
    public String getPayloadString() {
        if (payload.nonEmpty()) {
            return payload.toUtf8String();
        }
        return null;
    }

    /**
     * Sets CoAP payload data as String.
     *
     * @param payload payload
     */
    public void setPayload(String payload) {
        setPayload(Opaque.of(payload));
    }

    /**
     * Sets CoAP payload data as byte array.
     *
     * @param payload payload
     */
    public void setPayload(Opaque payload) {
        Objects.requireNonNull(payload);
        this.payload = payload;
    }

    public void setToken(Opaque token) {
        Objects.requireNonNull(token);
        if (token.size() > 8) {
            throw new IllegalArgumentException("Wrong TOKEN value, size should be within range 0-8");
        }
        this.token = token;
    }

    public Opaque getToken() {
        return token;
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
     * Indicates if this CoAP packet is Confirmable and must be acknowledged.
     *
     * @return true if this message must be acknowledged
     */
    public boolean isConfirmable() {
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

        if (printAddress && this.getRemoteAddrString() != null) {
            sb.append(this.getRemoteAddrString()).append(' ');
        }

        if (messageType != null) {
            sb.append(getMessageType().toString());
        }
        if (method != null) {
            sb.append(' ').append(method);
        }
        if (code != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(code.codeToString());
        }

        sb.append(" MID:").append(this.messageId);

        if (this.token.nonEmpty()) {
            sb.append(" Token:0x").append(this.token);
        }

        options.buildToString(sb);

        payloadToString(printFullPayload, printPayloadOnlyAsHex, doNotPrintPayload, sb);

        return sb.toString();
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private void payloadToString(boolean printFullPayload, boolean printPayloadOnlyAsHex, boolean doNotPrintPayload, StringBuilder sb) {
        if (payload.nonEmpty()) {
            if (doNotPrintPayload) {
                sb.append(" pl(").append(payload.size()).append(')');
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
                sb.append(" pl(").append(payload.size()).append("):'").append(decodedPayload, 0, 44).append("..'");
            }
        } else {
            if (!printFullPayload) {
                sb.append(" pl(").append(payload.size()).append("):0x").append(payload.toHex(22));
            } else {
                sb.append(" pl(").append(payload.size()).append("):0x").append(payload.toHex());
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

    public boolean isEmptyAck() {
        return messageType == MessageType.Acknowledgement && method == null && code == null && payload.isEmpty() && token.isEmpty();
    }

    boolean isRequest() {
        return method != null;
    }

    boolean isResponse() {
        return code != null;
    }

    public boolean isAck() {
        return messageType == MessageType.Acknowledgement;
    }

    public boolean isSeparateResponse() {
        return isResponse() && (messageType == null || messageType == MessageType.Confirmable || messageType == MessageType.NonConfirmable);
    }

    public void setTransportContext(TransportContext transportContext) {
        this.transportContext = Objects.requireNonNull(transportContext);
    }

    public TransportContext getTransportContext() {
        return transportContext;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.messageType);
        hash = 41 * hash + this.messageId;
        hash = 41 * hash + Objects.hashCode(this.code);
        hash = 41 * hash + Objects.hashCode(this.method);
        hash = 41 * hash + Objects.hashCode(this.payload);
        hash = 41 * hash + Objects.hashCode(this.remoteAddress);
        hash = 41 * hash + Objects.hashCode(this.options);
        hash = 41 * hash + Objects.hashCode(this.token);
        hash = 41 * hash + Objects.hashCode(this.transportContext);
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
        if (!Objects.equals(this.payload, other.payload)) {
            return false;
        }
        if (!Objects.equals(this.remoteAddress, other.remoteAddress)) {
            return false;
        }
        if (!Objects.equals(this.options, other.options)) {
            return false;
        }
        if (!Objects.equals(this.token, other.token)) {
            return false;
        }
        return Objects.equals(this.transportContext, other.transportContext);
    }

}
