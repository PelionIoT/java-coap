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

import static com.mbed.coap.utils.FutureHelpers.failedFuture;
import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.messaging.CoapTcpCSM;
import com.mbed.coap.utils.Service;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BlockWiseCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockWiseCallback.class);
    private static final int MAX_BLOCK_RESOURCE_CHANGE = 3;

    private CoapResponse response;
    CoapRequest request;
    private final Opaque requestPayload;
    private int resourceChanged;
    private final int numberOfBertBlocks;
    private final CoapTcpCSM csm;
    private final int maxIncomingBlockTransferSize;
    private final Service<CoapRequest, CoapResponse> sendService;


    BlockWiseCallback(Service<CoapRequest, CoapResponse> sendService, CoapTcpCSM csm, CoapRequest request, int maxIncomingBlockTransferSize) throws CoapException {
        this.request = request;
        this.requestPayload = request.getPayload();
        this.csm = csm;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.sendService = sendService;

        if (request.getMethod() != null && csm.useBlockTransfer(requestPayload)) {
            //request that needs to use blocks
            Opaque newPayload = BlockWiseTransfer.updateWithFirstBlock(request, csm);
            this.request = request.payload(newPayload);
            numberOfBertBlocks = csm.getBlockSize().numberOfBlocksPerMessage(csm.getMaxOutboundPayloadSize());

        } else {
            LOGGER.trace("makeRequest no block: {}", request);
            int maxPayloadSize = csm.getMaxOutboundPayloadSize();
            if (requestPayload.size() > maxPayloadSize) {
                throw new CoapException("Block transfers are not enabled for " + request.getPeerAddress() + " and payload size " + requestPayload.size() + " > max payload size " + maxPayloadSize);
            }
            numberOfBertBlocks = 0;
        }
    }

    CompletableFuture<CoapResponse> receive(CoapResponse response) {
        LOGGER.trace("BlockWiseCallback.call(): {}", response);

        if (response.getCode() == Code.C413_REQUEST_ENTITY_TOO_LARGE) {
            if (response.options().getBlock1Req() != null) {
                return restartBlockRequest(response.options().getBlock1Req().getBlockSize());
            } else {
                return completedFuture(response);
            }
        }

        CompletableFuture<CoapResponse> maybeFuture = handleIfBlock1(response);
        if (maybeFuture != null) {
            return maybeFuture;
        }

        if (response.options().getBlock2Res() != null) {
            return receiveBlock2(response);
        } else {
            return completedFuture(response);
        }
    }

    private CompletableFuture<CoapResponse> handleIfBlock1(CoapResponse response) {
        if (request.options().getBlock1Req() == null || response.options().getBlock1Req() == null) {
            return null;
        }

        if (!request.options().getBlock1Req().hasMore()) {
            return null;
        }

        if (response.getCode() != Code.C231_CONTINUE) {
            // if server report code other than 231_CONTINUE - abort transfer
            // see https://tools.ietf.org/html/draft-ietf-core-block-19#section-2.9
            LOGGER.warn("Error in block transfer: response=" + response);
            return completedFuture(response);
        }

        BlockOption responseBlock = response.options().getBlock1Req();
        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        BlockOption origReqBlock = request.options().getBlock1Req();
        if (responseBlock.getNr() == 0 && responseBlock.getSize() < origReqBlock.getSize()) {
            // adjust block number if remote replied with smaller block size
            // see: https://tools.ietf.org/html/rfc7959#section-2.5
            responseBlock = new BlockOption(responseBlock.getNr() + 1, responseBlock.getBlockSize(), origReqBlock.hasMore());
        } else {
            responseBlock = BlockWiseCallback.nextBertBlock(responseBlock, requestPayload.size(), numberOfBertBlocks, maxBlockPayload);
        }

        request.options().setBlock1Req(responseBlock);
        // reset size headers for all blocks except first
        // see https://tools.ietf.org/html/draft-ietf-core-block-18#section-4 , Implementation notes
        request.options().setSize1(null);
        Opaque blockPayload = BlockWiseTransfer.createBlockPart(responseBlock, requestPayload, maxBlockPayload);
        request = request.payload(blockPayload);
        LOGGER.trace("BlockWiseCallback.call() next block b1: {}", request);
        return makeRequest();
    }

    private CompletableFuture<CoapResponse> receiveBlock2(CoapResponse blResponse) {
        LOGGER.trace("Received CoAP block [{}]", blResponse.options().getBlock2Res());

        String errMsg = verifyBlockResponse(request.options().getBlock2Res(), blResponse);
        if (errMsg != null) {
            return failedFuture(new CoapBlockException(errMsg));
        }

        if (response == null) {
            response = blResponse;
        } else {
            this.response = new CoapResponse(blResponse.getCode(), response.getPayload().concat(blResponse.getPayload()), response.options());
            this.response.options().setBlock2Res(blResponse.options().getBlock2Res());

        }
        if (hasResourceChanged(blResponse)) {
            return restartBlockTransfer(blResponse);
        }

        if (response.getPayload().size() > maxIncomingBlockTransferSize) {
            return failedFuture(new CoapBlockTooLargeEntityException("Received too large entity for request, max allowed " + maxIncomingBlockTransferSize + ", received " + response.getPayload().size()));
        }

        BlockOption respBlockOption = blResponse.options().getBlock2Res();

        if (!respBlockOption.hasMore()) {
            //isCompleted = true;
            return completedFuture(response);
        } else {
            //isCompleted = false;
            //CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);

            int receivedBlocksCount = blResponse.getPayload().size() / respBlockOption.getBlockSize().getSize();

            request.options().setBlock2Res(new BlockOption(respBlockOption.getNr() + receivedBlocksCount, respBlockOption.getBlockSize(), false));
            request.options().setBlock1Req(null);
            LOGGER.trace("BlockWiseCallback.call() make next b2: {}", request);
            return makeRequest();
        }
    }

    private String verifyBlockResponse(BlockOption requestBlock, CoapResponse blResponse) {
        BlockOption responseBlock = blResponse.options().getBlock2Res();
        if (requestBlock != null && requestBlock.getNr() != responseBlock.getNr()) {
            String msg = "Requested and received block number mismatch: req=" + requestBlock + ", resp=" + responseBlock + ", stopping transaction";
            LOGGER.warn(msg + " [req: " + request.toString() + ", resp: " + blResponse + "]");
            return msg;
        }

        if (!BlockWiseTransfer.isBlockPacketValid(blResponse.getPayload(), responseBlock)) {
            return "Intermediate block size mismatch with block option " + responseBlock + " and payload size " + blResponse.getPayload().size();
        }
        if (!BlockWiseTransfer.isLastBlockPacketValid(blResponse.getPayload(), responseBlock)) {
            return "Last block size mismatch with block option " + responseBlock + " and payload size " + blResponse.getPayload().size();
        }
        return null;
    }

    private boolean hasResourceChanged(CoapResponse blResponse) {
        return !Objects.equals(response.options().getEtag(), blResponse.options().getEtag());
    }

    private CompletableFuture<CoapResponse> restartBlockTransfer(CoapResponse blResponse) {
        //resource representation has changed, start from beginning
        resourceChanged++;
        if (resourceChanged >= MAX_BLOCK_RESOURCE_CHANGE) {
            LOGGER.trace("CoAP resource representation has changed {}, giving up.", resourceChanged);
            return failedFuture(new CoapBlockException("Resource representation has changed too many times."));
        }
        LOGGER.trace("CoAP resource representation has changed while getting blocks");
        response = null;
        request.options().setBlock2Res(new BlockOption(0, blResponse.options().getBlock2Res().getBlockSize(), false));
        return makeRequest();
    }

    private CompletableFuture<CoapResponse> restartBlockRequest(BlockSize newSize) {
        BlockOption block1Req = new BlockOption(0, newSize, true);
        request.options().setBlock1Req(block1Req);

        Opaque blockPayload = BlockWiseTransfer.createBlockPart(block1Req, requestPayload, block1Req.getSize());
        request = request.payload(blockPayload);

        return makeRequest();
    }

    private CompletableFuture<CoapResponse> makeRequest() {
        return sendService.apply(request).thenCompose(this::receive);
    }


    static BlockOption nextBertBlock(BlockOption blockOption, int fullPayloadSize, int lastBlocksCountPerMessage, int maxPayloadSizePerBlock) {

        int nextBlockNumber = blockOption.getNr() + (blockOption.isBert() ? lastBlocksCountPerMessage : 1);
        int nextPayloadPos = nextBlockNumber * blockOption.getSize();
        int leftPayload = fullPayloadSize - nextPayloadPos;

        boolean newHasMore = blockOption.isBert()
                ? leftPayload > maxPayloadSizePerBlock
                : leftPayload > blockOption.getSize();

        return new BlockOption(nextBlockNumber, blockOption.getBlockSize(), newHasMore);
    }
}
