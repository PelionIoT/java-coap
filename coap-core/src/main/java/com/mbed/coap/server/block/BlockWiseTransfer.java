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
package com.mbed.coap.server.block;

import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.server.messaging.CoapTcpCSM;

class BlockWiseTransfer {

    static Opaque updateWithFirstBlock(SeparateResponse resp, CoapTcpCSM csm) {
        BlockOption blockOption = new BlockOption(0, csm.getBlockSize(), true);

        resp.options().setBlock1Req(null);
        resp.options().setBlock2Res(blockOption);
        resp.options().setSize1(null);
        resp.options().setSize2Res(resp.getPayload().size());

        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        return createBlockPart(blockOption, resp.getPayload(), maxBlockPayload);
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
        int numOfBlockPerMessage = blockOption.getBlockSize().numberOfBlocksPerMessage(maxPayloadSizePerBlock);
        return fullPayload.fragment(blockOption.getNr(), blockOption.getSize(), numOfBlockPerMessage);
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
