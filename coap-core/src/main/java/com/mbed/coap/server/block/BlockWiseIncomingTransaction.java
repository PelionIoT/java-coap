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
package com.mbed.coap.server.block;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapRequestEntityIncomplete;
import com.mbed.coap.exception.CoapRequestEntityTooLarge;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.messaging.Capabilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BlockWiseIncomingTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockWiseIncomingTransaction.class.getName());

    private final ByteArrayOutputStream payload;
    private final int maxIncomingBlockTransferSize;
    private final Capabilities csm;

    BlockWiseIncomingTransaction(CoapRequest request, int maxIncomingBlockTransferSize, Capabilities csm) {
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.csm = csm;
        Integer expectedPayloadSize = request.options().getSize1();
        BlockOption blockOption = request.options().getBlock1Req();

        int allocationSize = expectedPayloadSize != null ? expectedPayloadSize : blockOption.getSize() * 4;

        this.payload = new ByteArrayOutputStream(allocationSize);
    }

    void appendBlock(CoapRequest request) throws CoapCodeException {
        validateIncomingRequest(request);
        validateAlreadyReceivedPayloadSize(request);

        BlockOption reqBlock = request.options().getBlock1Req();

        int assumedCollectedPayloadSize = reqBlock.getNr() * reqBlock.getSize();
        if (payload.size() < assumedCollectedPayloadSize) {
            throw new CoapRequestEntityIncomplete();
        }

        try {
            // Don't append in case of resends.
            if (payload.size() == assumedCollectedPayloadSize) {
                request.getPayload().writeTo(payload);
            }
        } catch (IOException e) {
            // should never happen
            throw new CoapCodeException(Code.C500_INTERNAL_SERVER_ERROR, e);
        }
    }

    Opaque getCombinedPayload() {
        return Opaque.of(payload.toByteArray());
    }

    private void validateAlreadyReceivedPayloadSize(CoapRequest request) throws CoapRequestEntityTooLarge {
        int requestPayloadLength = request.getPayload().size();

        if (isTooBigPayloadSize(requestPayloadLength + payload.size())) {
            LOGGER.warn("Assembled block-transfer payload is too large: " + request);
            throw new CoapRequestEntityTooLarge(maxIncomingBlockTransferSize, "");
        }
    }

    private boolean isTooBigPayloadSize(int payloadSize) {
        return payloadSize > maxIncomingBlockTransferSize;
    }

    private void validateIncomingRequest(CoapRequest request) throws CoapCodeException {
        BlockOption reqBlock = request.options().getBlock1Req();
        // BERT request, but BERT support is not enabled on our server


        BlockSize agreedBlockSize = csm.getBlockSize();
        if (reqBlock.isBert() && (agreedBlockSize == null || !agreedBlockSize.isBert())) {
            LOGGER.warn("BERT is not supported for {}", request);
            throw new CoapCodeException(Code.C402_BAD_OPTION, "BERT is not supported");
        }

        if (!BlockWiseTransfer.isBlockPacketValid(request.getPayload(), reqBlock)) {
            LOGGER.warn("Intermediate block size does not match payload size {}", request);
            if (request.getPayload().size() > 0 && request.getPayload().size() < reqBlock.getSize() && !reqBlock.isBert()) {
                throw new CoapRequestEntityTooLarge(new BlockOption(0, agreedBlockSize, true), "");
            }
            throw new CoapCodeException(Code.C400_BAD_REQUEST, "block size mismatch");
        }

        if (!BlockWiseTransfer.isLastBlockPacketValid(request.getPayload(), reqBlock)) {
            LOGGER.warn("LAST block size does not match payload size {}", request);
            throw new CoapCodeException(Code.C400_BAD_REQUEST, "last block size mismatch");
        }

        if (request.options().getSize1() != null && isTooBigPayloadSize(request.options().getSize1())) {
            LOGGER.warn("Received request with too large size1 option: " + request);
            throw new CoapRequestEntityTooLarge(maxIncomingBlockTransferSize, "Entity too large");
        }
    }
}
