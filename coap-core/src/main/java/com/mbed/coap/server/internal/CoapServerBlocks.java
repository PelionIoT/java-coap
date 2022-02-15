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

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapHandler;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.FutureCallbackAdapter;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Block-wise transfer for CoAP server (RFC-7959)
 */
public class CoapServerBlocks extends CoapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerBlocks.class.getName());
    private final Map<BlockRequestId, BlockWiseIncomingTransaction> blockReqMap = new ConcurrentHashMap<>();
    private final CoapMessaging coapMessaging;
    private final CoapTcpCSMStorage capabilities;
    private final int maxIncomingBlockTransferSize;
    private final BlockWiseTransfer blockWiseTransfer;

    public CoapServerBlocks(CoapMessaging coapMessaging, CoapTcpCSMStorage capabilities, int maxIncomingBlockTransferSize) {
        super(coapMessaging);
        this.coapMessaging = coapMessaging;
        this.capabilities = capabilities;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
        this.blockWiseTransfer = new BlockWiseTransfer(capabilities);
    }

    @Override
    public CompletableFuture<CoapPacket> makeRequest(CoapPacket request, TransportContext outgoingTransContext) {
        FutureCallbackAdapter<CoapPacket> outerCallback = new FutureCallbackAdapter<>();

        try {
            BlockWiseCallback blockCallback = new BlockWiseCallback(
                    // make consequent requests with block priority and forces adding to queue even if it is full
                    callback -> coapMessaging.makePrioritisedRequest(callback.request, callback, outgoingTransContext),
                    capabilities.getOrDefault(request.getRemoteAddress()),
                    request,
                    outerCallback,
                    maxIncomingBlockTransferSize
            );

            coapMessaging.makeRequest(request, blockCallback, outgoingTransContext);
        } catch (CoapException e) {
            outerCallback.callException(e);
        }
        return outerCallback;
    }

    @Override
    public CompletableFuture<CoapPacket> sendNotification(CoapPacket notifPacket, TransportContext transContext) {
        if (useBlockTransfer(notifPacket, notifPacket.getRemoteAddress())) {
            //request that needs to use blocks
            blockWiseTransfer.updateWithFirstBlock(notifPacket);
        }
        return super.sendNotification(notifPacket, transContext);
    }

    private boolean useBlockTransfer(CoapPacket notifPacket, InetSocketAddress remoteAddress) {
        return capabilities.getOrDefault(remoteAddress).useBlockTransfer(notifPacket.getPayload());
    }

    @Override
    public void sendResponse(CoapExchange exchange) {
        CoapPacket resp = exchange.getResponse();
        if (resp != null && resp.headers().getBlock2Res() == null) {

            //check for blocking
            BlockOption block2Res = exchange.getRequest().headers().getBlock2Res();

            if (block2Res == null && useBlockTransfer(resp, exchange.getRemoteAddress())) {
                block2Res = new BlockOption(0, agreedBlockSize(exchange.getRemoteAddress()), true);
            }

            //if not notification with block
            if (block2Res != null
                    && !(exchange.getRequest().headers().getObserve() != null && exchange.getRequest().getCode() != null)) {
                updateBlockResponse(block2Res, resp, exchange);
            }
        }

        super.sendResponse(exchange);
    }

    private void updateBlockResponse(final BlockOption block2Response, final CoapPacket resp, final CoapExchange exchange) {
        BlockOption block2Res = block2Response;
        int blFrom = block2Res.getNr() * block2Res.getSize();

        int maxMessageSize = (!block2Res.isBert()) ? block2Res.getSize() : capabilities.getOrDefault(resp.getRemoteAddress()).getMaxOutboundPayloadSize();

        int blTo = blFrom + maxMessageSize;

        if (blTo + 1 >= resp.getPayload().length) {
            blTo = resp.getPayload().length;
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
        if (exchange.getRequest().headers().getSize2Res() != null && block2Res.getNr() == 0) {
            resp.headers().setSize2Res(resp.getPayload().length);
        }
        byte[] blockPayload = new byte[newLength];
        if (newLength > 0) {
            try {
                System.arraycopy(resp.getPayload(), blFrom, blockPayload, 0, newLength);
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
        resp.headers().setBlock2Res(block2Res);
        resp.setPayload(blockPayload);
    }

    private void removeBlockRequest(BlockRequestId blockRequestId) {
        blockReqMap.remove(blockRequestId);
    }

    @Override
    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext incomingTransContext) throws CoapException {

        BlockOption reqBlock = request.headers().getBlock1Req();

        if (reqBlock == null) {
            super.callRequestHandler(request, coapHandler, incomingTransContext);
            return;
        }

        //block wise transaction
        BlockRequestId blockRequestId = new BlockRequestId(request.headers().getUriPath(), request.getRemoteAddress());
        BlockWiseIncomingTransaction blockRequest = blockReqMap.get(blockRequestId);

        try {
            if (blockRequest == null && reqBlock.getNr() != 0) {
                //Could not find previous blocks
                LOGGER.warn("Could not find previous blocks for {}", request);
                throw new CoapCodeException(Code.C408_REQUEST_ENTITY_INCOMPLETE, "no prev blocks");
            } else if (blockRequest == null) {
                //start new block-wise transaction
                blockRequest = new BlockWiseIncomingTransaction(request, maxIncomingBlockTransferSize, capabilities.getOrDefault(request.getRemoteAddress()));
                blockReqMap.put(blockRequestId, blockRequest);
            }

            blockRequest.appendBlock(request);

        } catch (CoapCodeException e) {
            removeBlockRequest(blockRequestId);
            throw e;
        }

        if (!reqBlock.hasMore()) {
            //last block received
            request.setPayload(blockRequest.getCombinedPayload());

            CoapExchangeImplBlock exchange = new CoapExchangeImplBlock(request, this, incomingTransContext);
            coapHandler.handle(exchange);

            //remove from map
            removeBlockRequest(blockRequestId);
        } else {
            //more block available, send C231_CONTINUE
            BlockSize localBlockSize = agreedBlockSize(request.getRemoteAddress());

            if (localBlockSize != null && reqBlock.getSize() > localBlockSize.getSize()) {
                //to large block, change
                LOGGER.trace("to large block (" + reqBlock.getSize() + "), changing to " + localBlockSize.getSize());
                reqBlock = new BlockOption(reqBlock.getNr(), localBlockSize, reqBlock.hasMore());
            }

            CoapPacket response = request.createResponse(Code.C231_CONTINUE);
            response.headers().setBlock1Req(reqBlock);
            sendResponse(request, response, incomingTransContext);
        }
    }

    private BlockSize agreedBlockSize(InetSocketAddress address) {
        return capabilities.getOrDefault(address).getBlockSize();
    }

    private static class CoapExchangeImplBlock extends CoapExchangeImpl {

        public CoapExchangeImplBlock(CoapPacket request, CoapServer coapServer, TransportContext transContext) {
            super(request, coapServer, transContext);
        }

        @Override
        protected void send() {
            this.response.headers().setBlock1Req(this.request.headers().getBlock1Req());
            super.send();
        }
    }
}
