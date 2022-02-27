/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.CoapTcpCSMStorage;

class BlockWiseTransfer {
    private final CoapTcpCSMStorage capabilities;

    BlockWiseTransfer(CoapTcpCSMStorage capabilities) {
        this.capabilities = capabilities;
    }

    void updateWithFirstBlock(CoapPacket coapPacket) {
        CoapTcpCSM csm = capabilities.getOrDefault(coapPacket.getRemoteAddress());

        BlockOption blockOption = new BlockOption(0, csm.getBlockSize(), true);
        int payloadSize = coapPacket.getPayload().size();

        coapPacket.headers().setBlock1Req(null);
        coapPacket.headers().setBlock2Res(blockOption);
        coapPacket.headers().setSize1(null);
        coapPacket.headers().setSize2Res(payloadSize);

        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        Opaque blockPayload = createBlockPart(blockOption, coapPacket.getPayload(), maxBlockPayload);
        coapPacket.setPayload(blockPayload);
    }

    static Opaque updateWithFirstBlock(CoapRequest request, CoapTcpCSM csm) {
        BlockOption blockOption = new BlockOption(0, csm.getBlockSize(), true);
        int payloadSize = request.getPayload().size();

        request.options().setBlock1Req(blockOption);
        request.options().setBlock2Res(null);
        request.options().setSize1(payloadSize);
        request.options().setSize2Res(null);

        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        return createBlockPart(blockOption, request.getPayload(), maxBlockPayload);
    }

    static Opaque createBlockPart(BlockOption blockOption, Opaque fullPayload, int maxPayloadSizePerBlock) {
        //block size 16
        //b0: 0 - 15
        //b1: 16 - 31

        int startPos = blockOption.getNr() * blockOption.getSize();
        if (startPos > fullPayload.size() - 1) {
            //payload too small
            return fullPayload;
        }

        int blocksCount = blockOption.getBlockSize().numberOfBlocksPerMessage(maxPayloadSizePerBlock);

        // maxPayloadSize is not used to round len to blockSize
        // by default, maxPayloadSizePerBlock usually should be rounded to n*blockSize
        int len = blockOption.getSize() * blocksCount;
        if (startPos + len > fullPayload.size()) {
            len = fullPayload.size() - startPos;
            assert !blockOption.hasMore();
        }
        return fullPayload.slice(startPos, len);
    }


    static boolean isBlockPacketValid(Opaque payload, BlockOption blockOpt) {
        if (!blockOpt.hasMore()) {
            return true;
        }

        int payloadSize = payload.size();
        int blockSize = blockOpt.getSize();

        if (blockOpt.isBert()) {
            return payloadSize > 0 && payloadSize % blockSize == 0;
        } else {
            return payloadSize == blockSize;
        }
    }

    static boolean isLastBlockPacketValid(Opaque payload, BlockOption blockOpt) {
        if (blockOpt.hasMore()) {
            return true;
        }

        int payloadSize = payload.size();
        if (!blockOpt.isBert()) {
            return payloadSize <= blockOpt.getSize();
        }
        return true; // BERT last block size is always valid within max message size
    }

}
