/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package protocolTests.utils;

import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import java.net.InetSocketAddress;

/**
 * Created by szymon
 */
public class CoapPacketBuilder {
    private final CoapPacket coapPacket;

    private CoapPacketBuilder(InetSocketAddress address) {
        coapPacket = new CoapPacket(address);
    }

    public static CoapPacketBuilder newCoapPacket() {
        return new CoapPacketBuilder(null);
    }

    public static CoapPacketBuilder newCoapPacket(int mid) {
        CoapPacketBuilder coapPacketBuilder = new CoapPacketBuilder(null);
        coapPacketBuilder.mid(mid);
        return coapPacketBuilder;
    }

    public static CoapPacketBuilder newCoapPacket(InetSocketAddress address) {
        CoapPacketBuilder coapPacketBuilder = new CoapPacketBuilder(address);
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
        coapPacket.setToken(DataConvertingUtility.convertVariableUInt(token));
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

    public CoapPacketBuilder size1(Integer size) {
        coapPacket.headers().setSize1(size);
        return this;
    }

    public CoapPacketBuilder size2Res(Integer size) {
        coapPacket.headers().setSize2Res(size);
        return this;
    }

    public CoapPacketBuilder etag(int etag) {
        coapPacket.headers().setEtag(DataConvertingUtility.convertVariableUInt(etag));
        return this;
    }

    public CoapPacketBuilder con(Code code) {
        coapPacket.setMessageType(MessageType.Confirmable);
        coapPacket.setCode(code);
        return this;
    }

    public CoapPacketBuilder con() {
        coapPacket.setMessageType(MessageType.Confirmable);
        return this;
    }

    public CoapPacketBuilder non(Code code) {
        coapPacket.setMessageType(MessageType.NonConfirmable);
        coapPacket.setCode(code);
        return this;
    }
}
