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
package com.mbed.coap.server;

import com.mbed.coap.exception.CoapBlockException;
import com.mbed.coap.exception.CoapBlockTooLargeEntityException;
import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockOption;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.DataConvertingUtility;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.internal.CoapExchangeImpl;
import com.mbed.coap.server.internal.CoapTransaction;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Blocking mechanism for CoAP server (draft-ietf-core-block-16)
 *
 * @author szymon
 */
abstract class CoapServerBlocks extends CoapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerBlocks.class.getName());
    private static final int MAX_BLOCK_RESOURCE_CHANGE = 3;
    private final Map<BlockRequestId, BlockRequest> blockReqMap = new HashMap<>();
    private CoapTransaction.Priority blockCoapTransactionPriority = CoapTransaction.Priority.HIGH;

    CoapServerBlocks() {
        super();
    }

    public void setBlockCoapTransactionPriority(CoapTransaction.Priority blockCoapTransactionPriority) {
        this.blockCoapTransactionPriority = blockCoapTransactionPriority;
    }

    @Override
    public void makeRequest(CoapPacket request, Callback<CoapPacket> outerCallback, TransportContext outgoingTransContext) {
        if (outerCallback == null) {
            throw new NullPointerException("CallBack is null");
        }
        if (outerCallback instanceof BlockCallback) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("makeRequest block: " + request.toString());
            }
            // make consequent requests with block priority and forces adding to queue even if it is full
            super.makeRequestInternal(request, outerCallback, outgoingTransContext, blockCoapTransactionPriority, true);
            return;
        }
        BlockCallback blockCallback = new BlockCallback(request, wrapCallback(outerCallback), outgoingTransContext);

        if (request.getMethod() != null && request.getPayload() != null
                && this.getBlockSize() != null
                && request.getPayload().length > this.getBlockSize().getSize()) {
            //request that needs to use blocks
            BlockOption blockOption = new BlockOption(0, this.getBlockSize(), true);
            int payloadSize = request.getPayload().length;

            if (request.getMethod() == Method.GET) {
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
            byte[] nwPayload = blockOption.createBlockPart(request.getPayload());
            request.setPayload(nwPayload);

            // make first request with default priority and no forcing addition to queue
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("makeRequest first block: " + request.toString());
            }
            super.makeRequest(request, blockCallback, outgoingTransContext);
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("makeRequest no block: " + request.toString());
            }
            super.makeRequest(request, blockCallback, outgoingTransContext);
        }
    }

    @Override
    protected void sendResponse(CoapExchange exchange) {
        CoapPacket resp = exchange.getResponse();
        if (resp != null) {

            //check for blocking
            BlockOption block2Res = exchange.getRequest().headers().getBlock2Res();

            if (block2Res == null && this.getBlockSize() != null
                    && resp.getPayload() != null
                    && resp.getPayload().length > this.getBlockSize().getSize()) {
                block2Res = new BlockOption(0, getBlockSize(), true);
            }

            //if not notification with block
            if (block2Res != null && !(exchange.getRequest().headers().getObserve() != null && exchange.getRequest().getCode() != null)) {
                updateBlockResponse(block2Res, resp, exchange);
            }
        }

        super.sendResponse(exchange);
    }

    private static void updateBlockResponse(final BlockOption block2Response, final CoapPacket resp, final CoapExchange exchange) {
        BlockOption block2Res = block2Response;
        int blFrom = block2Res.getNr() * block2Res.getSize();
        int blTo = blFrom + block2Res.getSize();

        if (blTo + 1 >= resp.getPayload().length) {
            blTo = resp.getPayload().length;
            block2Res = new BlockOption(block2Res.getNr(), block2Res.getSzx(), false);
        } else {
            block2Res = new BlockOption(block2Res.getNr(), block2Res.getSzx(), true);
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

    protected void removeBlockRequest(BlockRequestId blockRequestId) {
        blockReqMap.remove(blockRequestId);
    }

    @Override
    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext incomingTransContext) throws CoapException {
        if (request.headers().getBlock1Req() != null) {
            //blocking request
            BlockOption reqBlock = request.headers().getBlock1Req();
            BlockRequestId blockRequestId = new BlockRequestId(request.headers().getUriPath(), request.getRemoteAddress());
            BlockRequest blockRequest = blockReqMap.get(blockRequestId);
            if (blockRequest == null) {
                if (reqBlock.getNr() == 0) {
                    //new blocking
                    LOGGER.trace("callRequestHandler() new block transfer");
                    blockRequest = new BlockRequest(request);
                    addBlockRequest(blockRequest);
                } else {
                    //Could not find previous blocks
                    CoapExchangeImpl exchange = new CoapExchangeImpl(request, this);
                    exchange.setResponseCode(Code.C408_REQUEST_ENTITY_INCOMPLETE);
                    exchange.getResponseHeaders().setBlock1Req(reqBlock);
                    exchange.getResponse().setToken(request.getToken());
                    exchange.sendResponse();
                    return;
                }
            } else {
                LOGGER.trace("callRequestHandler() block transfer continuation " + reqBlock);
            }

            //boolean isTokenMismatch = (blockRequest.token==null && request.headers().getToken()==null);
            boolean isTokenMismatch = (blockRequest.token != null && !Arrays.equals(blockRequest.token, request.getToken()))
                    || (blockRequest.token == null && request.getToken() != null);

            if (isTokenMismatch) {
                //token mismatch, send error, stop collecting blocks
                LOGGER.trace("callRequestHandler() block token mismatch " + reqBlock);
                CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, incomingTransContext);
                exchange.setResponseCode(Code.C408_REQUEST_ENTITY_INCOMPLETE);
                exchange.getResponseHeaders().setBlock1Req(reqBlock);
                exchange.getResponse().setToken(request.getToken());
                exchange.setResponseBody("Token mismatch");
                exchange.sendResponse();

                //remove from map
                removeBlockRequest(blockRequestId);
                return;

            }

            blockRequest.appendBlock(request);

            if (getMaxIncomingBlockTransferSize() > 0 && blockRequest.payload.length > getMaxIncomingBlockTransferSize()) {
                CoapExchangeImpl exchange = new CoapExchangeImpl(request, this, incomingTransContext);
                exchange.setResponseCode(Code.C413_REQUEST_ENTITY_TOO_LARGE);
                exchange.getResponseHeaders().setBlock1Req(reqBlock);
                exchange.getResponseHeaders().setSize1(getMaxIncomingBlockTransferSize());
                exchange.getResponse().setToken(request.getToken());
                exchange.setResponseBody("Entity too large");
                exchange.sendResponse();

                removeBlockRequest(blockRequestId);
                LOGGER.warn("Received request with too large entity: " + request.toString());
                return;
            }

            if (!reqBlock.hasMore()) {
                //last block received
                request.setPayload(blockRequest.payload);

                CoapExchangeImplBlock exchange = new CoapExchangeImplBlock(request, this, incomingTransContext);
                coapHandler.handle(exchange);

                //remove from map
                removeBlockRequest(blockRequestId);
            } else {
                //more block available, send ACK
                if (this.getBlockSize() != null && reqBlock.getSize() > this.getBlockSize().getSize()) {
                    //to large block, change
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("to large block (" + reqBlock.getSize() + "), changing to " + this.getBlockSize().getSize());
                    }
                    reqBlock = new BlockOption(reqBlock.getNr(), this.getBlockSize(), reqBlock.hasMore());
                }
                CoapExchangeImpl exchange = new CoapExchangeImpl(request, this);
                exchange.setResponseCode(Code.C231_CONTINUE);
                exchange.getResponseHeaders().setBlock1Req(reqBlock);
                exchange.getResponse().setToken(request.getToken());
                exchange.sendResponse();
            }

        } else {
            super.callRequestHandler(request, coapHandler, incomingTransContext);
        }
    }

    private class BlockCallback implements RequestCallback, CoapTransactionCallback {

        private final RequestCallback reqCallback;
        private CoapPacket response;
        private final CoapPacket request;
        private final byte[] requestPayload;
        private int resourceChanged;
        private final TransportContext outgoingTransContext;

        public BlockCallback(CoapPacket request, RequestCallback reqCallback, TransportContext outgoingTransContext) {
            this.reqCallback = reqCallback;
            this.request = request;
            this.requestPayload = request.getPayload();
            this.outgoingTransContext = outgoingTransContext;
        }

        @Override
        public void call(CoapPacket response) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("BlockCallback.call(): " + response.toString(false));
            }
            if (request != null && request.headers().getBlock1Req() != null && response.headers().getBlock1Req() != null) {
                BlockOption reqBlock = response.headers().getBlock1Req();
                if (request.headers().getBlock1Req().hasMore()) {
                    if (response.getCode() != Code.C231_CONTINUE) {
                        // if server report code other than 231_CONTINUE - abort transfer
                        // see https://tools.ietf.org/html/draft-ietf-core-block-19#section-2.9
                        LOGGER.warn("Error in block transfer: response=" + response);
                        reqCallback.call(response);
                        return;
                    }
                    //create new request
                    reqBlock = reqBlock.nextBlock(requestPayload);
                    request.headers().setBlock1Req(reqBlock);
                    // reset size headers for all blocks except first
                    // see https://tools.ietf.org/html/draft-ietf-core-block-18#section-4 , Implementation notes
                    request.headers().setSize1(null);
                    request.setPayload(reqBlock.createBlockPart(requestPayload));
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("BlockCallback.call() next block b1: " + request.toString(false));
                    }
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
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received CoAP block [" + blResponse.headers().getBlock2Res() + "]");
            }

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

            if (getMaxIncomingBlockTransferSize() > 0 && response.getPayload().length > getMaxIncomingBlockTransferSize()) {
                throw new CoapBlockTooLargeEntityException("Received too large entity for request, max allowed " + getMaxIncomingBlockTransferSize() + ", received " + response.getPayload().length);
            }

            if (!blResponse.headers().getBlock2Res().hasMore()) {
                //isCompleted = true;
                reqCallback.call(response);
            } else {
                //isCompleted = false;
                //CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);

                request.headers().setBlock2Res(new BlockOption(blResponse.headers().getBlock2Res().getNr() + 1, blResponse.headers().getBlock2Res().getSzx(), false));
                request.headers().setBlock1Req(null);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("BlockCallback.call() make next b2: " + request.toString(false));
                }
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

            if (responseBlock != null && responseBlock.hasMore() && responseBlock.getSize() != blResponse.getPayload().length) {
                throw new CoapBlockException("Block size mismatch with payload size " + responseBlock.getSize() + " != " + blResponse.getPayload().length);
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
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("CoAP resource representation has changed " + resourceChanged + ", giving up.");
                }
                reqCallback.callException(new CoapCodeException(Code.C408_REQUEST_ENTITY_INCOMPLETE));
                return true;
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("CoAP resource representation has changed while getting blocks");
            }
            response = null;
            request.headers().setBlock2Res(new BlockOption(0, blResponse.headers().getBlock2Res().getSzx(), false));
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
        private byte[] payload;
        private final String uriPath;
        private final InetSocketAddress sourceAddress;
        private final byte[] token;
        private final BlockRequestId blockRequestId;

        public BlockRequest(CoapPacket request) {
            this.payload = request.getPayload();
            this.uriPath = request.headers().getUriPath();
            this.sourceAddress = request.getRemoteAddress();
            this.token = request.getToken();

            this.blockRequestId = new BlockRequestId(uriPath, sourceAddress);
        }

        public BlockRequestId getBlockRequestId() {
            return blockRequestId;
        }

        private void appendBlock(CoapPacket request) {
            byte[] reqPayload = request.getPayload();
            BlockOption reqBlock = request.headers().getBlock1Req();
            this.payload = reqBlock.appendPayload(payload, reqPayload);
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
