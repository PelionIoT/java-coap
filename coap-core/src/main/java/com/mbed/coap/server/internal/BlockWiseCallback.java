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

import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.packet.Opaque;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BlockWiseCallback implements Callback<CoapPacket> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockWiseCallback.class);
    private static final int MAX_BLOCK_RESOURCE_CHANGE = 3;

    private final Callback<CoapPacket> reqCallback;
    private CoapPacket response;
    final CoapPacket request;
    private final Opaque requestPayload;
    private int resourceChanged;
    private final int numberOfBertBlocks;
    private final CoapTcpCSM csm;
    private final int maxIncomingBlockTransferSize;
    private final Consumer<BlockWiseCallback> makeRequestFunc;


    BlockWiseCallback(Consumer<BlockWiseCallback> makeRequestFunc, CoapTcpCSM csm, CoapPacket request, Callback<CoapPacket> reqCallback, int maxIncomingBlockTransferSize) throws CoapException {
        this.reqCallback = reqCallback;
        this.request = request;
        this.requestPayload = request.getPayload();
        this.csm = csm;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.makeRequestFunc = makeRequestFunc;

        if (request.getMethod() != null && csm.useBlockTransfer(requestPayload)) {
            //request that needs to use blocks
            numberOfBertBlocks = BlockWiseTransfer.updateWithFirstBlock(request, csm);

        } else {
            LOGGER.trace("makeRequest no block: {}", request);
            int maxPayloadSize = csm.getMaxOutboundPayloadSize();
            if (requestPayload.size() > maxPayloadSize) {
                throw new CoapException("Block transfers are not enabled for " + request.getRemoteAddress() + " and payload size " + requestPayload.size() + " > max payload size " + maxPayloadSize);
            }
            numberOfBertBlocks = 0;
        }
    }

    @Override
    public void call(CoapPacket response) {
        LOGGER.trace("BlockWiseCallback.call(): {}", response);

        if (response.getCode() == Code.C413_REQUEST_ENTITY_TOO_LARGE) {
            if (response.headers().getBlock1Req() != null) {
                restartBlockRequest(response.headers().getBlock1Req().getBlockSize());
            } else {
                reqCallback.call(response);
            }
            return;
        }

        if (handleIfBlock1(response)) {
            return;
        }

        if (response.headers().getBlock2Res() != null) {
            try {
                receiveBlock2(response);
            } catch (CoapBlockException ex) {
                reqCallback.callException(ex);
            }
        } else {
            reqCallback.call(response);
        }
    }

    private boolean handleIfBlock1(CoapPacket response) {
        if (request.headers().getBlock1Req() == null || response.headers().getBlock1Req() == null) {
            return false;
        }

        if (!request.headers().getBlock1Req().hasMore()) {
            return false;
        }

        if (response.getCode() != Code.C231_CONTINUE) {
            // if server report code other than 231_CONTINUE - abort transfer
            // see https://tools.ietf.org/html/draft-ietf-core-block-19#section-2.9
            LOGGER.warn("Error in block transfer: response=" + response);
            reqCallback.call(response);
            return true;
        }

        BlockOption responseBlock = response.headers().getBlock1Req();
        int maxBlockPayload = csm.getMaxOutboundPayloadSize();
        BlockOption origReqBlock = request.headers().getBlock1Req();
        if (responseBlock.getNr() == 0 && responseBlock.getSize() < origReqBlock.getSize()) {
            // adjust block number if remote replied with smaller block size
            // see: https://tools.ietf.org/html/rfc7959#section-2.5
            responseBlock = new BlockOption(responseBlock.getNr() + 1, responseBlock.getBlockSize(), origReqBlock.hasMore());
        } else {
            responseBlock = BlockWiseCallback.nextBertBlock(responseBlock, requestPayload.size(), numberOfBertBlocks, maxBlockPayload);
        }

        request.headers().setBlock1Req(responseBlock);
        // reset size headers for all blocks except first
        // see https://tools.ietf.org/html/draft-ietf-core-block-18#section-4 , Implementation notes
        request.headers().setSize1(null);
        ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(maxBlockPayload);
        BlockWiseTransfer.createBlockPart(responseBlock, requestPayload, blockPayload, maxBlockPayload);
        request.setPayload(Opaque.of(blockPayload.toByteArray()));
        LOGGER.trace("BlockWiseCallback.call() next block b1: {}", request);
        makeRequest();
        return true;
    }

    @Override
    public void callException(Exception ex) {
        reqCallback.callException(ex);
    }

    private void receiveBlock2(CoapPacket blResponse) throws CoapBlockException {
        LOGGER.trace("Received CoAP block [{}]", blResponse.headers().getBlock2Res());

        verifyBlockResponse(request.headers().getBlock2Res(), blResponse);

        if (response == null) {
            response = blResponse;
        } else {
            this.response.setPayload(response.getPayload().concat(blResponse.getPayload()));
            this.response.headers().setBlock2Res(blResponse.headers().getBlock2Res());
            this.response.setCode(blResponse.getCode());
        }
        if (hasResourceChanged(blResponse)) {
            restartBlockTransfer(blResponse);
            return;
        }

        if (response.getPayload().size() > maxIncomingBlockTransferSize) {
            throw new CoapBlockTooLargeEntityException("Received too large entity for request, max allowed " + maxIncomingBlockTransferSize + ", received " + response.getPayload().size());
        }

        BlockOption respBlockOption = blResponse.headers().getBlock2Res();

        if (!respBlockOption.hasMore()) {
            //isCompleted = true;
            reqCallback.call(response);
        } else {
            //isCompleted = false;
            //CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);

            int receivedBlocksCount = blResponse.getPayload().size() / respBlockOption.getBlockSize().getSize();

            request.headers().setBlock2Res(new BlockOption(respBlockOption.getNr() + receivedBlocksCount, respBlockOption.getBlockSize(), false));
            request.headers().setBlock1Req(null);
            LOGGER.trace("BlockWiseCallback.call() make next b2: {}", request);
            makeRequest();
        }
    }

    private void verifyBlockResponse(BlockOption requestBlock, CoapPacket blResponse) throws CoapBlockException {
        BlockOption responseBlock = blResponse.headers().getBlock2Res();
        if (requestBlock != null && requestBlock.getNr() != responseBlock.getNr()) {
            String msg = "Requested and received block number mismatch: req=" + requestBlock + ", resp=" + responseBlock + ", stopping transaction";
            LOGGER.warn(msg + " [req: " + request.toString() + ", resp: " + blResponse.toString(false, false, true, true) + "]");
            throw new CoapBlockException(msg);
        }

        if (!BlockWiseTransfer.isBlockPacketValid(blResponse, responseBlock)) {
            throw new CoapBlockException("Intermediate block size mismatch with block option " + responseBlock.toString() + " and payload size " + blResponse.getPayload().size());
        }
        if (!BlockWiseTransfer.isLastBlockPacketValid(blResponse, responseBlock)) {
            throw new CoapBlockException("Last block size mismatch with block option " + responseBlock + " and payload size " + blResponse.getPayload().size());
        }
    }

    private boolean hasResourceChanged(CoapPacket blResponse) {
        return !Objects.equals(response.headers().getEtag(), blResponse.headers().getEtag());
    }

    private void restartBlockTransfer(CoapPacket blResponse) {
        //resource representation has changed, start from beginning
        resourceChanged++;
        if (resourceChanged >= MAX_BLOCK_RESOURCE_CHANGE) {
            LOGGER.trace("CoAP resource representation has changed {}, giving up.", resourceChanged);
            reqCallback.callException(new CoapBlockException("Resource representation has changed too many times."));
            return;
        }
        LOGGER.trace("CoAP resource representation has changed while getting blocks");
        response = null;
        request.headers().setBlock2Res(new BlockOption(0, blResponse.headers().getBlock2Res().getBlockSize(), false));
        makeRequest();
    }

    private void restartBlockRequest(BlockSize newSize) {
        BlockOption block1Req = new BlockOption(0, newSize, true);
        request.headers().setBlock1Req(block1Req);

        ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(block1Req.getSize());
        BlockWiseTransfer.createBlockPart(block1Req, requestPayload, blockPayload, block1Req.getSize());
        request.setPayload(Opaque.of(blockPayload.toByteArray()));

        makeRequest();
    }

    private void makeRequest() {
        makeRequestFunc.accept(this);
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
