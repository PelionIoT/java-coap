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
package com.mbed.coap.server.internal;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapRequestEntityIncomplete;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by szymon
 */
class BlockWiseIncomingTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockWiseIncomingTransaction.class.getName());

    private final ByteArrayOutputStream payload;
    private final int maxIncomingBlockTransferSize;
    private final CoapTcpCSM csm;

    BlockWiseIncomingTransaction(CoapPacket request, int maxIncomingBlockTransferSize, CoapTcpCSM csm) {
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.csm = csm;
        Integer expectedPayloadSize = request.headers().getSize1();
        BlockOption blockOption = request.headers().getBlock1Req();

        int allocationSize = expectedPayloadSize != null ? expectedPayloadSize : blockOption.getSize() * 4;

        this.payload = new ByteArrayOutputStream(allocationSize);
    }

    void appendBlock(CoapPacket request) throws CoapCodeException {
        validateIncomingRequest(request);
        validateAlreadyReceivedPayloadSize(request);

        BlockOption reqBlock = request.headers().getBlock1Req();

        int assumedCollectedPayloadSize = reqBlock.getNr() * reqBlock.getSize();
        if (payload.size() < assumedCollectedPayloadSize) {
            throw new CoapRequestEntityIncomplete();
        }

        try {
            // Don't append in case of resends.
            if (payload.size() == assumedCollectedPayloadSize) {
                payload.write(request.getPayload());
            }
        } catch (IOException e) {
            // should never happen
            throw new CoapCodeException(Code.C500_INTERNAL_SERVER_ERROR, e);
        }
    }

    byte[] getCombinedPayload() {
        return payload.toByteArray();
    }

    private void validateAlreadyReceivedPayloadSize(CoapPacket request) throws CoapRequestEntityTooLarge {
        int requestPayloadLength = request.getPayload().length;

        if (isTooBigPayloadSize(requestPayloadLength + payload.size())) {
            LOGGER.warn("Assembled block-transfer payload is too large: " + request.toString());
            throw new CoapRequestEntityTooLarge(maxIncomingBlockTransferSize, "");
        }
    }

    private boolean isTooBigPayloadSize(int payloadSize) {
        return payloadSize > maxIncomingBlockTransferSize;
    }

    private void validateIncomingRequest(CoapPacket request) throws CoapCodeException {
        BlockOption reqBlock = request.headers().getBlock1Req();
        // BERT request, but BERT support is not enabled on our server


        BlockSize agreedBlockSize = csm.getBlockSize();
        if (reqBlock.isBert() && (agreedBlockSize == null || !agreedBlockSize.isBert())) {
            LOGGER.warn("BERT is not supported for {}", request);
            throw new CoapCodeException(Code.C402_BAD_OPTION, "BERT is not supported");
        }

        if (!BlockWiseTransfer.isBlockPacketValid(request, reqBlock)) {
            LOGGER.warn("Intermediate block size does not match payload size {}", request);
            if (request.getPayload().length > 0 && request.getPayload().length < reqBlock.getSize() && !reqBlock.isBert()) {
                throw new CoapRequestEntityTooLarge(new BlockOption(0, agreedBlockSize, true), "");
            }
            throw new CoapCodeException(Code.C400_BAD_REQUEST, "block size mismatch");
        }

        if (!BlockWiseTransfer.isLastBlockPacketValid(request, reqBlock)) {
            LOGGER.warn("LAST block size does not match payload size {}", request);
            throw new CoapCodeException(Code.C400_BAD_REQUEST, "last block size mismatch");
        }

        if (request.headers().getSize1() != null && isTooBigPayloadSize(request.headers().getSize1())) {
            LOGGER.warn("Received request with too large size1 option: " + request.toString());
            throw new CoapRequestEntityTooLarge(maxIncomingBlockTransferSize, "Entity too large");
        }
    }
}
