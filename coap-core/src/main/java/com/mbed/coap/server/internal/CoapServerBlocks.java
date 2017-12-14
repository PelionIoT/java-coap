/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.server.CoapHandler;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.server.CoapTransactionCallback;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Blocking mechanism for CoAP server (draft-ietf-core-block-16)
 *
 * @author szymon
 */
public class CoapServerBlocks extends CoapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerBlocks.class.getName());
    private static final int MAX_BLOCK_RESOURCE_CHANGE = 3;
    private final Map<BlockRequestId, BlockRequest> blockReqMap = new ConcurrentHashMap<>();
    private final CoapMessaging coapMessaging;
    private final CoapTcpCSMStorage capabilities;
    private final int maxIncomingBlockTransferSize;

    public CoapServerBlocks(CoapMessaging coapMessaging, CoapTcpCSMStorage capabilities, int maxIncomingBlockTransferSize) {
        super(coapMessaging);
        this.coapMessaging = coapMessaging;
        this.capabilities = capabilities;
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
    }

    @Override
    public void makeRequest(CoapPacket request, Callback<CoapPacket> outerCallback, TransportContext outgoingTransContext) {
        if (outerCallback == null) {
            throw new NullPointerException("CallBack is null");
        }
        if (outerCallback instanceof BlockCallback) {
            LOGGER.trace("makeRequest block: {}", request);
            // make consequent requests with block priority and forces adding to queue even if it is full
            coapMessaging.makePrioritisedRequest(request, outerCallback, outgoingTransContext);
            return;
        }

        BlockCallback blockCallback = new BlockCallback(request, wrapCallback(outerCallback), outgoingTransContext);

        if (request.getMethod() != null && isBlockTransfer(request, request.getRemoteAddress())) {
            //request that needs to use blocks
            BlockOption blockOption = new BlockOption(0, agreedBlockSize(request.getRemoteAddress()), true);
            int payloadSize = request.getPayload().length;

            setInitialBlockOptions(request, payloadSize, blockOption, request.getMethod() == Method.GET);


            byte[] firstPayloadBlock = blockCallback.createFirstPayloadBlockAndUpdateBlocksCount(blockOption);
            request.setPayload(firstPayloadBlock);

            // make first request with default priority and no forcing addition to queue
            LOGGER.trace("makeRequest first block: {}", request);
            super.makeRequest(request, blockCallback, outgoingTransContext);
        } else {
            LOGGER.trace("makeRequest no block: {}", request);
            int payloadSize = request.getPayload().length;
            int maxPayloadSize = capabilities.getOrDefault(request.getRemoteAddress()).getMaxOutboundPayloadSize();
            if (payloadSize > maxPayloadSize) {
                blockCallback.callException(
                        new CoapException("Block transfers are not enabled for " + request.getRemoteAddress() + " and payload size " + payloadSize + " > max payload size " + maxPayloadSize)
                );
                return;
            }
            coapMessaging.makeRequest(request, blockCallback, outgoingTransContext);
        }
    }

    @Override
    public void sendNotification(CoapPacket notifPacket, Callback<CoapPacket> callback, TransportContext transContext) {
        if (isBlockTransfer(notifPacket, notifPacket.getRemoteAddress())) {
            //request that needs to use blocks
            BlockOption blockOption = new BlockOption(0, agreedBlockSize(notifPacket.getRemoteAddress()), true);
            int payloadSize = notifPacket.getPayload().length;

            setInitialBlockOptions(notifPacket, payloadSize, blockOption, true);

            int maxBlockPayload = capabilities.getOrDefault(notifPacket.getRemoteAddress()).getMaxOutboundPayloadSize();
            ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(maxBlockPayload);
            blockOption.createBlockPart(notifPacket.getPayload(), blockPayload, maxBlockPayload);

            notifPacket.setPayload(blockPayload.toByteArray());
        }
        super.sendNotification(notifPacket, callback, transContext);
    }

    @Override
    public void sendResponse(CoapExchange exchange) {
        CoapPacket resp = exchange.getResponse();
        if (resp != null) {

            //check for blocking
            BlockOption block2Res = exchange.getRequest().headers().getBlock2Res();

            if (block2Res == null && isBlockTransfer(resp, exchange.getRemoteAddress())) {
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

    private void setInitialBlockOptions(CoapPacket request, int payloadSize, BlockOption blockOption, boolean isBlock2) {
        if (isBlock2) {
            request.headers().setBlock1Req(null);
            request.headers().setBlock2Res(blockOption);
            request.headers().setSize1(null);
            request.headers().setSize2Res(payloadSize);
        } else {
            request.headers().setBlock1Req(blockOption);
            request.headers().setBlock2Res(null);
            request.headers().setSize1(payloadSize);
            request.headers().setSize2Res(null);
        }
    }

    private boolean isBlockTransfer(CoapPacket requestOrResponse, InetSocketAddress remoteAddress) {
        BlockSize blockSize = agreedBlockSize(remoteAddress);

        return blockSize != null
                && requestOrResponse.getPayload() != null
                && requestOrResponse.getPayload().length > capabilities.getOrDefault(requestOrResponse.getRemoteAddress()).getMaxOutboundPayloadSize();
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

    /**
     * @param blockRequest block request
     * @throws CoapException may be thrown by an overriding implementation
     */
    protected void addBlockRequest(BlockRequest blockRequest) throws CoapException {
        blockReqMap.put(blockRequest.getBlockRequestId(), blockRequest);
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

        //blocking request
        BlockRequestId blockRequestId = new BlockRequestId(request.headers().getUriPath(), request.getRemoteAddress());
        BlockRequest blockRequest = blockReqMap.get(blockRequestId);

        try {
            checkBlocksRequest(blockRequest, request, incomingTransContext);

            if (blockRequest != null) {
                LOGGER.trace("callRequestHandler() block transfer continuation {}", request);
            }

            // getNr() != 0 checked in checkBlockRequest()
            if (blockRequest == null && reqBlock.getNr() == 0) {
                //new blocking
                LOGGER.trace("callRequestHandler() new block transfer");
                blockRequest = new BlockRequest(request);
                addBlockRequest(blockRequest);
            }

            checkTokenMatch(blockRequest, request, incomingTransContext);

            blockRequest.appendBlock(request);

            checkAlreadyReceivedPayloadSize(blockRequest, request, incomingTransContext);

        } catch (BlockCheckFailedException e) {
            removeBlockRequest(blockRequestId);
            return;
        }

        if (!reqBlock.hasMore()) {
            //last block received
            request.setPayload(blockRequest.payload.toByteArray());

            CoapExchangeImplBlock exchange = new CoapExchangeImplBlock(request, this, incomingTransContext);
            coapHandler.handle(exchange);

            //remove from map
            removeBlockRequest(blockRequestId);
        } else {
            //more block available, send ACK
            BlockSize localBlockSize = agreedBlockSize(request.getRemoteAddress());

            if (localBlockSize != null && reqBlock.getSize() > localBlockSize.getSize()) {
                //to large block, change
                LOGGER.trace("to large block (" + reqBlock.getSize() + "), changing to " + localBlockSize.getSize());
                reqBlock = new BlockOption(reqBlock.getNr(), localBlockSize, reqBlock.hasMore());
            }
            CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, incomingTransContext);
            exchange.setResponseCode(Code.C231_CONTINUE);
            exchange.getResponseHeaders().setBlock1Req(reqBlock);
            exchange.getResponse().setToken(request.getToken());
            exchange.sendResponse();
        }
    }

    private void checkBlocksRequest(BlockRequest existingBlockRequest, CoapPacket request, TransportContext incomingTransContext) throws BlockCheckFailedException {
        BlockOption reqBlock = request.headers().getBlock1Req();
        // BERT request, but BERT support is not enabled on our server

        if (existingBlockRequest == null && reqBlock.getNr() != 0) {
            //Could not find previous blocks
            createBlockErrorResponse(request, incomingTransContext,
                    Code.C408_REQUEST_ENTITY_INCOMPLETE, "no prev blocks")
                    .sendResponse();
            LOGGER.warn("Could not find previous blocks for {}", request);
            throw new BlockCheckFailedException();
        }

        BlockSize agreedBlockSize = agreedBlockSize(request.getRemoteAddress());
        if (reqBlock.isBert() && (agreedBlockSize == null || !agreedBlockSize.isBert())) {
            createBlockErrorResponse(request, incomingTransContext, Code.C402_BAD_OPTION, "BERT is not supported").sendResponse();
            LOGGER.warn("BERT is not supported for {}", request);
            throw new BlockCheckFailedException();
        }

        if (reqBlock.hasMore() && !checkIntermediateBlockSize(request, reqBlock)) {
            createBlockErrorResponse(request, incomingTransContext, Code.C400_BAD_REQUEST, "block size mismatch")
                    .sendResponse();
            LOGGER.warn("Intermediate block size does not match payload size {}", request);
            throw new BlockCheckFailedException();
        }

        if (!reqBlock.hasMore() && !checkLastBlockSize(request, reqBlock)) {
            createBlockErrorResponse(request, incomingTransContext, Code.C400_BAD_REQUEST, "last block size mismatch")
                    .sendResponse();
            LOGGER.warn("LAST block size does not match payload size {}", request);
            throw new BlockCheckFailedException();
        }

        if (request.headers().getSize1() != null && isTooBigPayloadSize(request.headers().getSize1())) {
            CoapExchangeImpl exchange = createBlockErrorResponse(request, incomingTransContext, Code.C413_REQUEST_ENTITY_TOO_LARGE, "Entity too large");
            exchange.getResponseHeaders().setSize1(maxIncomingBlockTransferSize);
            exchange.sendResponse();

            LOGGER.warn("Received request with too large size1 option: " + request.toString());
            throw new BlockCheckFailedException();
        }
    }

    private void checkTokenMatch(BlockRequest blockRequest, CoapPacket request, TransportContext incomingTransContext) throws BlockCheckFailedException {
        boolean isTokenMismatch = (blockRequest.token != null && !Arrays.equals(blockRequest.token, request.getToken()))
                || (blockRequest.token == null && request.getToken() != null);

        if (isTokenMismatch) {
            //token mismatch, send error, stop collecting blocks
            LOGGER.warn("callRequestHandler() block token mismatch {}: {}", blockRequest.token, request);
            createBlockErrorResponse(request, incomingTransContext,
                    Code.C408_REQUEST_ENTITY_INCOMPLETE, "Token mismatch")
                    .sendResponse();
            throw new BlockCheckFailedException();
        }
    }

    private void checkAlreadyReceivedPayloadSize(BlockRequest blockRequest, CoapPacket request, TransportContext incomingTransContext) throws BlockCheckFailedException {
        if (isTooBigPayloadSize(blockRequest.payload.size())) {
            CoapExchangeImpl exchange = createBlockErrorResponse(request, incomingTransContext, Code.C413_REQUEST_ENTITY_TOO_LARGE, "Entity too large");
            exchange.getResponseHeaders().setSize1(maxIncomingBlockTransferSize);
            exchange.sendResponse();

            LOGGER.warn("Assembled block-transfer payload is too large: " + request.toString());
            throw new BlockCheckFailedException();
        }
    }

    private boolean isTooBigPayloadSize(int payloadSize) {
        return maxIncomingBlockTransferSize > 0 && payloadSize > maxIncomingBlockTransferSize;
    }

    private boolean checkIntermediateBlockSize(CoapPacket packet, BlockOption blockHeader) {
        int payloadSize = packet.getPayload().length;

        int blockSize = blockHeader.getSize();

        if (blockHeader.isBert()) {
            return payloadSize % blockSize == 0;
        } else {
            return payloadSize == blockSize;
        }
    }

    private boolean checkLastBlockSize(CoapPacket packet, BlockOption blockOption) {
        int payloadSize = packet.getPayload().length;
        if (!blockOption.isBert()) {
            return payloadSize <= blockOption.getSize();
        }
        return true; // BERT last block size is always valid within max message size
    }

    private CoapExchangeImpl createBlockErrorResponse(CoapPacket request, TransportContext transContext, Code responseCode, String bodyMessage) {
        CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, transContext);
        exchange.setResponseCode(responseCode);
        exchange.getResponseHeaders().setBlock1Req(request.headers().getBlock1Req());
        exchange.getResponse().setToken(request.getToken());
        if (bodyMessage != null) {
            exchange.setResponseBody(bodyMessage);
        }
        return exchange;
    }

    private BlockSize agreedBlockSize(InetSocketAddress address) {
        return capabilities.getOrDefault(address).getBlockSize();
    }

    private class BlockCheckFailedException extends Exception {
    }

    private class BlockCallback implements RequestCallback, CoapTransactionCallback {

        private final RequestCallback reqCallback;
        private CoapPacket response;
        private final CoapPacket request;
        private final byte[] requestPayload;
        private int resourceChanged;
        private final TransportContext outgoingTransContext;
        private int lastBertBlocksCount;
        private final CoapTcpCSM csm;

        public BlockCallback(CoapPacket request, RequestCallback reqCallback, TransportContext outgoingTransContext) {
            this.reqCallback = reqCallback;
            this.request = request;
            this.requestPayload = request.getPayload();
            this.outgoingTransContext = outgoingTransContext;
            this.csm = capabilities.getOrDefault(request.getRemoteAddress());
        }

        public byte[] createFirstPayloadBlockAndUpdateBlocksCount(BlockOption reqBlock) {
            int maxBlockPayload = csm.getMaxOutboundPayloadSize();
            ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(maxBlockPayload);
            lastBertBlocksCount = reqBlock.createBlockPart(requestPayload, blockPayload, maxBlockPayload);
            return blockPayload.toByteArray();
        }

        @Override
        public void call(CoapPacket response) {
            LOGGER.trace("BlockCallback.call(): {}", response);
            if (request != null && request.headers().getBlock1Req() != null && response.headers().getBlock1Req() != null) {
                BlockOption responseBlock = response.headers().getBlock1Req();
                if (request.headers().getBlock1Req().hasMore()) {
                    if (response.getCode() != Code.C231_CONTINUE) {
                        // if server report code other than 231_CONTINUE - abort transfer
                        // see https://tools.ietf.org/html/draft-ietf-core-block-19#section-2.9
                        LOGGER.warn("Error in block transfer: response=" + response);
                        reqCallback.call(response);
                        return;
                    }

                    int maxBlockPayload = csm.getMaxOutboundPayloadSize();
                    BlockOption origReqBlock = request.headers().getBlock1Req();
                    if (responseBlock.getNr() == 0 && responseBlock.getSize() < origReqBlock.getSize()) {
                        // adjust block number if remote replied with smaller block size
                        // see: https://tools.ietf.org/html/rfc7959#section-2.5
                        responseBlock = new BlockOption(responseBlock.getNr() + 1, responseBlock.getBlockSize(), origReqBlock.hasMore());
                    } else {
                        responseBlock = responseBlock.nextBertBlock(requestPayload, lastBertBlocksCount, maxBlockPayload);
                    }

                    request.headers().setBlock1Req(responseBlock);
                    // reset size headers for all blocks except first
                    // see https://tools.ietf.org/html/draft-ietf-core-block-18#section-4 , Implementation notes
                    request.headers().setSize1(null);
                    ByteArrayOutputStream blockPayload = new ByteArrayOutputStream(maxBlockPayload);
                    lastBertBlocksCount = responseBlock.createBlockPart(requestPayload, blockPayload, maxBlockPayload);
                    request.setPayload(blockPayload.toByteArray());
                    LOGGER.trace("BlockCallback.call() next block b1: {}", request);
                    makeRequest(request, outgoingTransContext);
                    return;
                }
            }

            if (response.headers().getBlock2Res() != null) {
                try {
                    receiveBlock(response);
                } catch (CoapBlockException ex) {
                    reqCallback.callException(ex);
                } catch (CoapException ex) {
                    LOGGER.warn(ex.getLocalizedMessage(), ex);
                    reqCallback.callException(ex);
                }
            } else {
                reqCallback.call(response);
            }
        }

        private void receiveBlock(CoapPacket blResponse) throws CoapException {
            LOGGER.trace("Received CoAP block [{}]", blResponse.headers().getBlock2Res());

            verifyBlockResponse(request.headers().getBlock2Res(), blResponse);

            if (response == null) {
                response = blResponse;
            } else {
                this.response.setPayload(DataConvertingUtility.combine(response.getPayload(), blResponse.getPayload()));
                this.response.headers().setBlock2Res(blResponse.headers().getBlock2Res());
                this.response.setCode(blResponse.getCode());
            }
            if (hasResourceChanged(blResponse)) {
                restartBlockTransfer(blResponse);
                return;
            }

            if (maxIncomingBlockTransferSize > 0 && response.getPayload().length > maxIncomingBlockTransferSize) {
                throw new CoapBlockTooLargeEntityException("Received too large entity for request, max allowed " + maxIncomingBlockTransferSize + ", received " + response.getPayload().length);
            }

            BlockOption respBlockOption = blResponse.headers().getBlock2Res();

            if (!respBlockOption.hasMore()) {
                //isCompleted = true;
                reqCallback.call(response);
            } else {
                //isCompleted = false;
                //CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);

                int receivedBlocksCount = blResponse.getPayload().length / respBlockOption.getBlockSize().getSize();

                request.headers().setBlock2Res(new BlockOption(respBlockOption.getNr() + receivedBlocksCount, respBlockOption.getBlockSize(), false));
                request.headers().setBlock1Req(null);
                LOGGER.trace("BlockCallback.call() make next b2: {}", request);
                makeRequest(request, outgoingTransContext);
                if (reqCallback instanceof CoapTransactionCallback) {
                    ((CoapTransactionCallback) reqCallback).blockReceived();
                }
            }
        }

        private void verifyBlockResponse(BlockOption requestBlock, CoapPacket blResponse) throws CoapBlockException {
            BlockOption responseBlock = blResponse.headers().getBlock2Res();
            if (requestBlock != null && (responseBlock == null || requestBlock.getNr() != responseBlock.getNr())) {
                String msg = "Requested and received block number mismatch: req=" + requestBlock + ", resp=" + responseBlock + ", stopping transaction";
                LOGGER.warn(msg + " [req: " + request.toString() + ", resp: " + blResponse.toString() + "]");
                throw new CoapBlockException(msg);
            }

            if (responseBlock != null) {
                if (responseBlock.hasMore() && !checkIntermediateBlockSize(blResponse, responseBlock)) {
                    throw new CoapBlockException("Intermediate block size mismatch with block option " + responseBlock.toString() + " and payload size " + blResponse.getPayload().length);
                }
                if (!responseBlock.hasMore() && !checkLastBlockSize(blResponse, responseBlock)) {
                    throw new CoapBlockException("Last block size mismatch with block option " + responseBlock + " and payload size " + blResponse.getPayload().length);
                }
            }
        }

        private boolean hasResourceChanged(CoapPacket blResponse) {
            return !(response.headers().getEtag() == null && blResponse.headers().getEtag() == null)
                    && response.headers().getEtag() != null && !(Arrays.equals(response.headers().getEtag(), blResponse.headers().getEtag()));
        }

        private boolean restartBlockTransfer(CoapPacket blResponse) throws CoapException {
            //resource representation has changed, start from beginning
            resourceChanged++;
            if (resourceChanged > MAX_BLOCK_RESOURCE_CHANGE) {
                LOGGER.trace("CoAP resource representation has changed {}, giving up.", resourceChanged);
                reqCallback.callException(new CoapCodeException(Code.C408_REQUEST_ENTITY_INCOMPLETE));
                return true;
            }
            LOGGER.trace("CoAP resource representation has changed while getting blocks");
            response = null;
            request.headers().setBlock2Res(new BlockOption(0, blResponse.headers().getBlock2Res().getBlockSize(), false));
            makeRequest(request, outgoingTransContext);
            return false;
        }

        @Override
        public void callException(Exception ex) {
            reqCallback.callException(ex);
        }

        private void makeRequest(CoapPacket request, TransportContext outgoingTransContext) {
            CoapServerBlocks.this.makeRequest(request, this, outgoingTransContext);
        }

        @Override
        public void messageResent() {
            if (reqCallback instanceof CoapTransactionCallback) {
                ((CoapTransactionCallback) reqCallback).messageResent();
            }
        }

        @Override
        public void blockReceived() {
            if (reqCallback instanceof CoapTransactionCallback) {
                ((CoapTransactionCallback) reqCallback).blockReceived();
            }
        }

        @Override
        public void onSent() {
            reqCallback.onSent();
        }
    }

    protected static class BlockRequestId {

        private final String uriPath;
        private final InetSocketAddress sourceAddress;

        public BlockRequestId(String uriPath, InetSocketAddress sourceAddress) {
            this.uriPath = uriPath;
            this.sourceAddress = sourceAddress;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 73 * hash + (this.uriPath != null ? this.uriPath.hashCode() : 0);
            hash = 73 * hash + (this.sourceAddress != null ? this.sourceAddress.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BlockRequestId other = (BlockRequestId) obj;
            if ((this.uriPath == null) ? (other.uriPath != null) : !this.uriPath.equals(other.uriPath)) {
                return false;
            }
            if (this.sourceAddress != other.sourceAddress && (this.sourceAddress == null || !this.sourceAddress.equals(other.sourceAddress))) {
                return false;
            }
            return true;
        }

    }

    protected static class BlockRequest {

        //private CoapPacket request;
        private final ByteArrayOutputStream payload;
        private final String uriPath;
        private final InetSocketAddress sourceAddress;
        private final byte[] token;
        private final BlockRequestId blockRequestId;
        private int lastWrittenBlocksCount;

        public BlockRequest(CoapPacket request) {
            Integer expectedPayloadSize = request.headers().getSize1();
            BlockOption blockOption = request.headers().getBlock1Req();

            int allocationSize = expectedPayloadSize != null && expectedPayloadSize >= BlockSize.S_16.getSize() ?
                    expectedPayloadSize : blockOption.getSize() * 4;

            this.payload = new ByteArrayOutputStream(allocationSize);
            this.uriPath = request.headers().getUriPath();
            this.sourceAddress = request.getRemoteAddress();
            this.token = request.getToken();

            this.blockRequestId = new BlockRequestId(uriPath, sourceAddress);
        }

        public BlockRequestId getBlockRequestId() {
            return blockRequestId;
        }

        private int appendBlock(CoapPacket request) {
            byte[] reqPayload = request.getPayload();
            BlockOption reqBlock = request.headers().getBlock1Req();

            if (payload.size() != reqBlock.getNr() * reqBlock.getSize()) {
                //size has changed
                byte[] receivedBytes = payload.toByteArray();
                payload.reset();
                payload.write(receivedBytes, 0, reqBlock.getNr() * reqBlock.getSize());
            }

            lastWrittenBlocksCount = reqBlock.appendPayload(payload, reqPayload);
            return lastWrittenBlocksCount;
        }
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
