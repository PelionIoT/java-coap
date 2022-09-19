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
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.server.messaging.CapabilitiesResolver;
import com.mbed.coap.utils.Filter;
import com.mbed.coap.utils.Service;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockWiseIncomingFilter implements Filter.SimpleFilter<CoapRequest, CoapResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockWiseIncomingFilter.class.getName());
    private final Map<BlockRequestId, BlockWiseIncomingTransaction> blockReqMap = new ConcurrentHashMap<>();
    private final CapabilitiesResolver capabilities;
    private final int maxIncomingBlockTransferSize;

    public BlockWiseIncomingFilter(CapabilitiesResolver capabilities, int maxIncomingBlockTransferSize) {
        this.capabilities = capabilities;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request, Service<CoapRequest, CoapResponse> service) {
        BlockOption reqBlock = request.options().getBlock1Req();

        if (reqBlock == null) {
            final CoapRequest coapRequest = request;
            return service.apply(request)
                    .thenApply(resp -> adjustPayloadSize(coapRequest, resp));
        }

        //block wise transaction
        BlockRequestId blockRequestId = new BlockRequestId(request.options().getUriPath(), request.getPeerAddress());
        BlockWiseIncomingTransaction blockRequest = blockReqMap.get(blockRequestId);

        try {
            if (blockRequest == null && reqBlock.getNr() != 0) {
                //Could not find previous blocks
                LOGGER.warn("Could not find previous blocks for {}", request);
                throw new CoapCodeException(Code.C408_REQUEST_ENTITY_INCOMPLETE, "no prev blocks");
            } else if (blockRequest == null) {
                //start new block-wise transaction
                blockRequest = new BlockWiseIncomingTransaction(request, maxIncomingBlockTransferSize, capabilities.getOrDefault(request.getPeerAddress()));
                blockReqMap.put(blockRequestId, blockRequest);
            }

            blockRequest.appendBlock(request);

        } catch (CoapCodeException e) {
            removeBlockRequest(blockRequestId);
            return failedFuture(e);
        }

        if (!reqBlock.hasMore()) {
            //last block received
            request = request.payload(blockRequest.getCombinedPayload());

            //remove from map
            removeBlockRequest(blockRequestId);
            final CoapRequest coapRequest = request;
            return service
                    .apply(request)
                    .thenApply(resp -> adjustPayloadSize(coapRequest, resp));
        } else {
            //more block available, send C231_CONTINUE
            BlockSize localBlockSize = agreedBlockSize(request.getPeerAddress());

            if (localBlockSize != null && reqBlock.getSize() > localBlockSize.getSize()) {
                //to large block, change
                LOGGER.trace("to large block (" + reqBlock.getSize() + "), changing to " + localBlockSize.getSize());
                reqBlock = new BlockOption(reqBlock.getNr(), localBlockSize, reqBlock.hasMore());
            }

            CoapResponse response = CoapResponse.of(Code.C231_CONTINUE);
            response.options().setBlock1Req(reqBlock);
            return completedFuture(response);
        }
    }

    private BlockSize agreedBlockSize(InetSocketAddress address) {
        return capabilities.getOrDefault(address).getBlockSize();
    }

    private void removeBlockRequest(BlockRequestId blockRequestId) {
        blockReqMap.remove(blockRequestId);
    }

    public CoapResponse adjustPayloadSize(CoapRequest req, CoapResponse resp) {
        resp.options().setBlock1Req(req.options().getBlock1Req());
        if (resp.options().getBlock2Res() == null) {

            //check for blocking
            BlockOption block2Res = req.options().getBlock2Res();

            if (block2Res == null && capabilities.getOrDefault(req.getPeerAddress()).useBlockTransfer(resp.getPayload())) {
                block2Res = new BlockOption(0, agreedBlockSize(req.getPeerAddress()), true);
            }

            if (block2Res != null && req.options().getObserve() == null) {
                return updateBlockResponse(block2Res, req, resp);
            }
        }

        return resp;
    }

    private CoapResponse updateBlockResponse(final BlockOption block2Response, final CoapRequest req, final CoapResponse resp) {
        BlockOption block2Res = block2Response;
        int blFrom = block2Res.getNr() * block2Res.getSize();

        int maxMessageSize = (!block2Res.isBert()) ? block2Res.getSize() : capabilities.getOrDefault(req.getPeerAddress()).getMaxOutboundPayloadSize();

        int blTo = blFrom + maxMessageSize;

        if (blTo + 1 >= resp.getPayload().size()) {
            blTo = resp.getPayload().size();
            block2Res = new BlockOption(block2Res.getNr(), block2Res.getBlockSize(), false);
        } else {
            block2Res = new BlockOption(block2Res.getNr(), block2Res.getBlockSize(), true);
        }
        int newLength = blTo - blFrom;
        if (newLength < 0) {
            newLength = 0;
        }
        // reply with payload size only in first block
        // see https://tools.ietf.org/html/draft-ietf-core-block-18#section-4 , Implementation notes
        if (req.options().getSize2Res() != null && block2Res.getNr() == 0) {
            resp.options().setSize2Res(resp.getPayload().size());
        }
        Opaque blockPayload = resp.getPayload().slice(blFrom, newLength);
        resp.options().setBlock2Res(block2Res);
        return resp.payload(blockPayload);
    }

}
