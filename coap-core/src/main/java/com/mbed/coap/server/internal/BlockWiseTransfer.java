/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.server.CoapTcpCSMStorage;
import java.io.ByteArrayOutputStream;

class BlockWiseTransfer {
    private final CoapTcpCSMStorage capabilities;

    BlockWiseTransfer(CoapTcpCSMStorage capabilities) {
        this.capabilities = capabilities;
    }

    int updateWithFirstBlock(CoapPacket coapPacket) {
        return updateWithFirstBlock(coapPacket, capabilities.getOrDefault(coapPacket.getRemoteAddress()));
    }

    static int updateWithFirstBlock(CoapPacket coapPacket, CoapTcpCSM csm) {
        BlockOption blockOption = new BlockOption(0, csm.getBlockSize(), true);
        int payloadSize = coapPacket.getPayload().length;

        boolean isBlock2 = coapPacket.getMethod() == null;

        if (isBlock2) {
            coapPacket.headers().setBlock1Req(null);
            coapPacket.headers().setBlock2Res(blockOption);
            coapPacket.headers().setSize1(null);
            coapPacket.headers().setSize2Res(payloadSize);
        } else {
            coapPacket.headers().setBlock1Req(blockOption);
            coapPacket.headers().setBlock2Res(null);
            coapPacket.headers().setSize1(payloadSize);
            coapPacket.headers().setSize2Res(null);
        }

        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(maxBlockPayload);
        int blocks = createBlockPart(blockOption, coapPacket.getPayload(), blockPayload, maxBlockPayload);
        coapPacket.setPayload(blockPayload.toByteArray());

        return blocks;
    }

    /**
     * Creates new block, saves it into outputBlock and retuns count of block put to the payload according
     * to maxPayloadSizePerBlock
     *
     * @param fullPayload - full payload from which block will be created
     * @param outputBlock - outputStream where block will be saved
     * @param maxPayloadSizePerBlock - maximum payload size (mostly for BERT blocks)
     * @return
     */
    static int createBlockPart(BlockOption blockOption, byte[] fullPayload, ByteArrayOutputStream outputBlock, int maxPayloadSizePerBlock) {
        //block size 16
        //b0: 0 - 15
        //b1: 16 - 31

        int startPos = blockOption.getNr() * blockOption.getSize();
        if (startPos > fullPayload.length - 1) {
            //payload too small
            return 0;
        }

        int blocksCount = blockOption.getBlockSize().numberOfBlocksPerMessage(maxPayloadSizePerBlock);

        // maxPayloadSize is not used to round len to blockSize
        // by default, maxPayloadSizePerBlock usually should be rounded to n*blockSize
        int len = blockOption.getSize() * blocksCount;
        if (startPos + len > fullPayload.length) {
            len = fullPayload.length - startPos;
            assert !blockOption.hasMore();
        }
        outputBlock.write(fullPayload, startPos, len);

        return blocksCount;
    }


    static boolean isBlockPacketValid(CoapPacket packet, BlockOption blockOpt) {
        if (!blockOpt.hasMore()) {
            return true;
        }

        int payloadSize = packet.getPayload().length;
        int blockSize = blockOpt.getSize();

        if (blockOpt.isBert()) {
            return payloadSize > 0 && payloadSize % blockSize == 0;
        } else {
            return payloadSize == blockSize;
        }
    }

    static boolean isLastBlockPacketValid(CoapPacket packet, BlockOption blockOpt) {
        if (blockOpt.hasMore()) {
            return true;
        }

        int payloadSize = packet.getPayload().length;
        if (!blockOpt.isBert()) {
            return payloadSize <= blockOpt.getSize();
        }
        return true; // BERT last block size is always valid within max message size
    }

}
