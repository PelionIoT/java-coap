/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import org.mbed.coap.BlockOption;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;

/**
 * Created by szymon
 */
public class CoapPacketBuilder {
    private final CoapPacket coapPacket = new CoapPacket(null);

    public static CoapPacketBuilder newCoapPacket() {
        return new CoapPacketBuilder();
    }

    public static CoapPacketBuilder newCoapPacket(int mid) {
        CoapPacketBuilder coapPacketBuilder = new CoapPacketBuilder();
        coapPacketBuilder.mid(mid);
        return coapPacketBuilder;
    }

    public CoapPacket build() {
        return coapPacket;
    }

    public CoapPacketBuilder get() {
        coapPacket.setMethod(Method.GET);
        return this;
    }

    public CoapPacketBuilder put() {
        coapPacket.setMethod(Method.PUT);
        return this;
    }

    public CoapPacketBuilder ack(Code code) {
        coapPacket.setMessageType(MessageType.Acknowledgement);
        coapPacket.setCode(code);
        return this;
    }


    public CoapPacketBuilder uriPath(String uriPath) {
        coapPacket.headers().setUriPath(uriPath);
        return this;
    }

    public CoapPacketBuilder contFormat(short contentFormat) {
        coapPacket.headers().setContentFormat(contentFormat);
        return this;
    }

    public CoapPacketBuilder mid(int mid) {
        coapPacket.setMessageId(mid);
        return this;
    }


    public CoapPacketBuilder payload(String payload) {
        coapPacket.setPayload(payload);
        return this;
    }

    public CoapPacketBuilder obs(int observe) {
        coapPacket.headers().setObserve(observe);
        return this;
    }

    public CoapPacketBuilder token(long token) {
        coapPacket.setToken(HeaderOptions.convertVariableUInt(token));
        return this;
    }

    public CoapPacketBuilder block2Res(int blockNr, BlockSize blockSize, boolean more) {
        coapPacket.headers().setBlock2Res(new BlockOption(blockNr, blockSize, more));
        return this;
    }

    public CoapPacketBuilder block1Req(int blockNr, BlockSize blockSize, boolean more) {
        coapPacket.headers().setBlock1Req(new BlockOption(blockNr, blockSize, more));
        return this;
    }

    public CoapPacketBuilder etag(int etag) {
        coapPacket.headers().setEtag(HeaderOptions.convertVariableUInt(etag));
        return this;
    }

}
