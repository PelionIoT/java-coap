/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.mbed.coap.BlockOption;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Blocking mechanism for CoAP server (draft-ietf-core-block-14)
 *
 * @author szymon
 */
class CoapServerBlocks extends CoapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoapServerBlocks.class);
    private static final int MAX_BLOCK_RESOURCE_CHANGE = 3;
    protected Map<BlockRequestId, BlockRequest> blockReqMap = new HashMap<>();
    private final Map<NotificationBlockId, byte[]> notifBlockMap = new HashMap<>();

    CoapServerBlocks() {
        super();
    }

    CoapServerBlocks(TransportConnector trans, Executor executor, CoapIdContext coapIdContext) {
        super(trans, executor, coapIdContext);
    }

    @Override
    public void makeRequest(CoapPacket request, Callback<CoapPacket> outerCallback, TransportContext outgoingTransContext) throws CoapException {
        if (outerCallback == null) {
            throw new NullPointerException();
        }
        if (outerCallback instanceof BlockCallback) {
            super.makeRequest(request, outerCallback, outgoingTransContext);
            return;
        }
        BlockCallback blockCallback = new BlockCallback(request, outerCallback, outgoingTransContext);

        if (request.getMethod() != null && request.getPayload() != null
                && this.getBlockSize() != null
                && request.getPayload().length > this.getBlockSize().getSize()) {
            //request that needs to use blocks

            request.headers().setBlock1Req(new BlockOption(0, this.getBlockSize(), true));
            byte[] nwPayload = request.headers().getBlock1Req().createBlockPart(request.getPayload());
            request.setPayload(nwPayload);

            super.makeRequest(request, blockCallback, outgoingTransContext);
        } else {
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

            if (block2Res != null) {
                sendBlockResponse(block2Res, resp, exchange);
            }
        }

        super.sendResponse(exchange);
    }

    private static void sendBlockResponse(BlockOption block2Res, CoapPacket resp, CoapExchange exchange) {
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
        if (exchange.getRequest().headers().getSize1() != null) {
            resp.headers().setSize1(resp.getPayload().length);
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
     * @throws CoapException may be thrown by an overriding implementation
     */
    protected void addBlockRequest(BlockRequest blockRequest) throws CoapException {
        blockReqMap.put(blockRequest.getBlockRequestId(), blockRequest);
    }

    protected void removeBlockRequest(BlockRequestId blockRequestId) {
        blockReqMap.remove(blockRequestId);
    }

    protected boolean isBlockReqMapEmpty() {
        return blockReqMap.isEmpty();
    }

    @Override
    protected void callRequestHandler(CoapPacket request, CoapHandler coapHandler, TransportContext incomingTransContext) throws CoapException {
        if (request.headers().getBlock1Req() != null) {
            //blocking request
            BlockOption reqBlock = request.headers().getBlock1Req();
            BlockRequestId blockRequestId = new BlockRequestId(request.headers().getUriPath(), request.getOtherEndAddress());
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
                    exchange.getRequestHeaders().setBlock1Req(reqBlock);
                    exchange.getRequest().setToken(request.getToken());
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
                exchange.getRequestHeaders().setBlock1Req(reqBlock);
                exchange.getRequest().setToken(request.getToken());
                exchange.setResponseBody("Token mismatch");
                exchange.sendResponse();

                //remove from map
                removeBlockRequest(blockRequestId);
                return;

            }

            blockRequest.appendBlock(request);

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

    @Override
    public void setObservationHandler(ObservationHandler outerObsHandler) {
        if (outerObsHandler != null) {
            super.setObservationHandler(new BlockObsCallback(outerObsHandler));
        } else {
            super.setObservationHandler(null);
        }
    }

    private class BlockObsCallback implements ObservationHandler {

        private final ObservationHandler wrappedObsHandler;

        public BlockObsCallback(ObservationHandler outerObsHandler) {
            this.wrappedObsHandler = outerObsHandler;
        }

        @Override
        public void call(final CoapExchange t) {
            if (t.getRequestHeaders().getBlock2Res() == null) {
                wrappedObsHandler.call(t);
                return;
            }

            NotificationBlockId notifBlockId = new NotificationBlockId(t.getRequest());
            LOGGER.debug("BlockObsCallback:call() " + t.getResponse());
            if (t.getRequestHeaders().getBlock2Res().getNr() == 0) {
                //new blocking notification message
                notifBlockMap.put(notifBlockId, t.getRequest().getPayload());
                //blockRequest = notifBlockMap.get(notifBlockId);
            } else {
                byte[] payload = notifBlockMap.get(notifBlockId);
                payload = t.getRequest().headers().getBlock2Res().appendPayload(payload, t.getRequestBody());
                notifBlockMap.put(notifBlockId, payload);
            }

            if (!t.getRequestHeaders().getBlock2Res().hasMore()) {
                t.getRequest().setPayload(notifBlockMap.get(notifBlockId));
                notifBlockMap.remove(notifBlockId);
                wrappedObsHandler.call(t);
                return;
            }
            t.sendResponse();

        }

        @Override
        public void callException(Exception ex) {
            wrappedObsHandler.callException(ex);
        }

        @Override
        public boolean hasObservation(byte[] token) {
            return wrappedObsHandler.hasObservation(token);
        }
    }

    private class BlockCallback implements Callback<CoapPacket>, CoapTransactionCallback {

        private final Callback<CoapPacket> reqCallback;
        private CoapPacket response;
        private final CoapPacket request;
        private final byte[] requestPayload;
        private final String requestUri;
        private int resourceChanged;
        private final InetSocketAddress destination;
        private final TransportContext outgoingTransContext;

        public BlockCallback(CoapPacket request, Callback<CoapPacket> reqCallback, TransportContext outgoingTransContext) {
            this.reqCallback = reqCallback;
            this.request = request;
            this.requestPayload = request.getPayload();
            this.requestUri = request.headers().getUriPath();
            this.destination = request.getOtherEndAddress();
            this.outgoingTransContext = outgoingTransContext;
        }

        @Override
        public void call(CoapPacket response) {
            if (request != null && request.headers().getBlock1Req() != null && response.headers().getBlock1Req() != null) {
                BlockOption reqBlock = response.headers().getBlock1Req();
                if (request.headers().getBlock1Req().hasMore()) {
                    //create new request
                    reqBlock = reqBlock.nextBlock(requestPayload);
                    request.headers().setBlock1Req(reqBlock);
                    request.setPayload(reqBlock.createBlockPart(requestPayload));
                    try {
                        makeRequest(request, outgoingTransContext);
                        return;
                    } catch (CoapException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                        reqCallback.callException(ex);
                        return;
                    }

                }

            }

            if (response.headers().getBlock2Res() != null) {
                try {
                    receiveBlock(response);
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
            if (response == null) {
                response = blResponse;
            } else {
                this.response.setPayload(combine(response.getPayload(), blResponse.getPayload()));
                this.response.headers().setBlock2Res(blResponse.headers().getBlock2Res());
                this.response.setCode(blResponse.getCode());
            }
            if (hasResourceChanged(blResponse)) {
                if (restartBlockTransfer(blResponse)) {
                    return;
                }
                return;
            }
            if (!blResponse.headers().getBlock2Res().hasMore()) {
                //isCompleted = true;
                reqCallback.call(response);
            } else {
                //isCompleted = false;
                //CoapPacket request = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);

                request.headers().setBlock2Res(new BlockOption(blResponse.headers().getBlock2Res().getNr() + 1, blResponse.headers().getBlock2Res().getSzx(), false));
                request.headers().setBlock1Req(null);
                makeRequest(request, outgoingTransContext);
                if (reqCallback instanceof CoapTransactionCallback) {
                    ((CoapTransactionCallback) reqCallback).blockReceived();
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
            CoapPacket cpRequest = new CoapPacket(Method.GET, MessageType.Confirmable, requestUri, destination);
            cpRequest.headers().setBlock2Res(new BlockOption(0, blResponse.headers().getBlock2Res().getSzx(), true));
            makeRequest(cpRequest, outgoingTransContext);
            return false;
        }

        @Override
        public void callException(Exception ex) {
            reqCallback.callException(ex);
        }

        private void makeRequest(CoapPacket request, TransportContext outgoingTransContext) throws CoapException {
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
    }

    private static byte[] combine(byte[] arr1, byte[] arr2) {
        byte[] newArr = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, newArr, 0, arr1.length);
        System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
        return newArr;
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

        public InetSocketAddress getSourceAddress() {
            return sourceAddress;
        }
    }

    private static class NotificationBlockId {

        private final byte[] token;
        private final Integer observation;
        private final InetSocketAddress sourceAddress;

        NotificationBlockId(CoapPacket request) {
            token = request.getToken();
            observation = request.headers().getObserve();
            sourceAddress = request.getOtherEndAddress();
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Arrays.hashCode(this.token);
            hash = 79 * hash + (this.observation != null ? this.observation.hashCode() : 0);
            hash = 79 * hash + (this.sourceAddress != null ? this.sourceAddress.hashCode() : 0);
            return hash;
        }

        @Override
        @SuppressWarnings({"PMD.NPathComplexity", "PMD.CyclomaticComplexity"}) //ignore for equals method
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NotificationBlockId other = (NotificationBlockId) obj;
            if (this.token != other.token && (this.token == null || !Arrays.equals(this.token, other.token))) {
                return false;
            }
            if (this.observation != other.observation && (this.observation == null || !this.observation.equals(other.observation))) {
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
            this.sourceAddress = request.getOtherEndAddress();
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

        public InetSocketAddress getSourceAddress() {
            return sourceAddress;
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
