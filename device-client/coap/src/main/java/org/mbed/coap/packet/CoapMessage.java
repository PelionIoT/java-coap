/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

/**
 * Interface for CoAP message.
 *
 * @author szymon
 */
public interface CoapMessage {

    /**
     * Returns payload as String.
     *
     * @return payload
     */
    String getPayloadString();

    /**
     * Return payload as byte array.
     *
     * @return payload
     */
    byte[] getPayload();

    /**
     * Returns CoAP code.
     *
     * @return code
     */
    Code getCode();

    /**
     * Returns method.
     *
     * @return method
     */
    Method getMethod();

    /**
     * Returns CoAP message type.
     *
     * @return message type
     */
    MessageType getMessageType();

    /**
     * Return CoAP header options object.
     *
     * @return header options
     */
    HeaderOptions headers();

    byte[] getToken();
}
