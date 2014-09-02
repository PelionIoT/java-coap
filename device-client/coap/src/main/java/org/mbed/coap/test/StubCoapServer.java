/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.test;

import org.mbed.coap.server.CoapServerBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapMessage;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.ExHeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapHandler;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.ObservationHandler;
import org.mbed.coap.transmission.CoapTimeout;
import org.mbed.coap.transmission.SingleTimeout;
import org.mbed.coap.transmission.TransmissionTimeout;
import org.mbed.coap.transport.TransportConnector;
import org.mbed.coap.utils.SyncCallback;

/**
 *
 * @author szymon
 */
public class StubCoapServer {

    static final Integer ANY_INTEGER = Integer.valueOf(0);
    static final Short ANY_SHORT = Short.valueOf((short) 0);
    static final Long ANY_LONG = Long.valueOf(0);
    static final byte[] ANY_BYTEARR = new byte[1];
    private final Map<CoapPacket, StubResponse> rules = new LinkedHashMap<>();
    private CoapServer server;
    private final Map<String, CoapPacket> requests = new HashMap<>();
    private long singleTimeout;
    private TransportConnector transportConnector;
    private BlockSize blockSize;
    private BlockingQueue<CoapPacket> notifQueue;

    public StubCoapServer(TransportConnector transportConnector) {
        this.transportConnector = transportConnector;
    }

    public StubCoapServer(TransportConnector transportConnector, long singleTimeout) {
        this.transportConnector = transportConnector;
        this.singleTimeout = singleTimeout;
    }

    public StubCoapServer(long singleTimeout) {
        this.singleTimeout = singleTimeout;
    }

    public StubCoapServer() {
        this.singleTimeout = 3000;
    }

    public StubCoapServer(CoapServer coapServer) {
        this.server = coapServer;
    }

    public void start() throws IOException {
        if (server == null) {
            TransmissionTimeout transTimeout = (singleTimeout > 0) ? new SingleTimeout(singleTimeout) : new CoapTimeout();
            server = CoapServerBuilder.newBuilder().transport(transportConnector).blockSize(blockSize).disableDuplicateCheck().timeout(transTimeout).build();
        }
        server.addRequestHandler("/*", new CoapHandler() {
            @Override
            public void handle(CoapExchange exchange) throws CoapException {
                StubCoapServer.this.handle(exchange);
            }
        });

        server.start();
    }

    public void stop() {
        server.stop();
    }

    public void setCoapServer(CoapServer coapServer) {
        server = coapServer;
    }

    public void setBlockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress("127.0.0.1", server.getLocalSocketAddress().getPort());    //NOPMD
    }

    public void handle(CoapExchange ex) throws CoapException {
        synchronized (requests) {
            requests.put(ex.getRequest().getMethod().toString() + " " + ex.getRequest().headers().getUriPath(), ex.getRequest());
            requests.notifyAll();
        }
        StubResponse resp = find(ex.getRequest());
        if (resp == null) {
            ex.setResponseCode(Code.C404_NOT_FOUND);
        } else {
            CoapPacket newResp = CoapPacket.read(resp.resp.toByteArray());
            newResp.setRemoteAddress(ex.getRemoteAddress());
            newResp.setMessageId(ex.getRequest().getMessageId());
            ex.setResponse(newResp);
            try {
                if (resp.delayMilli > 0) {
                    synchronized (this) {
                        this.wait(resp.delayMilli);
                    }
                } else if (resp.delayNotif) {
                    synchronized (this) {
                        this.wait();
                    }
                }
            } catch (InterruptedException interruptedException) {
                //ignore
            }
        }
        ex.sendResponse();
    }

    CoapMessage makeRequest(CoapPacket req) throws CoapException {
        SyncCallback<CoapPacket> callback = new SyncCallback<>();
        server.makeRequest(req, callback);
        try {
            return callback.getResponse();
        } catch (Exception ex) {
            throw new CoapException(ex);
        }
    }

    public RequestBuilder send(int port) {
        return new RequestBuilder(this, port, null);
    }

    public CoapClient client(int port) {
        return new CoapClient(new InetSocketAddress("localhost", port), server);
    }

    private StubResponse find(CoapPacket request) {
        StubResponse matchedResp = null;
        int matchedRank = -1;

        for (Map.Entry<CoapPacket, StubResponse> rule : rules.entrySet()) {
            ExHeaderOptions ruleHead = rule.getKey().headers();
            if (!ruleHead.getUriPath().equals(request.headers().getUriPath())) {
                continue;
            }
            if (rule.getKey().getMethod() != request.getMethod()) {
                if (matchedResp == null) {
                    matchedResp = new StubResponse(new CoapPacket(Code.C405_METHOD_NOT_ALLOWED, MessageType.Acknowledgement, null));
                }
                continue;
            }
            if (matchedResp == null || matchedResp.resp.getCode() == Code.C405_METHOD_NOT_ALLOWED) {
                matchedResp = new StubResponse(new CoapPacket(Code.C400_BAD_REQUEST, MessageType.Acknowledgement, null));
            }
            int currRank = 0;
            try {
                currRank = checkBodyAndHeaders(request, currRank, rule, ruleHead);

            } catch (NotMatchedException exception) {
                continue;
            }
            if (currRank > matchedRank) {
                //System.out.println(currRank + " [" + rule.getValue() + "] -> " + matchedRank + " [" + matchedResp + "]");
                matchedResp = rule.getValue();
                matchedRank = currRank;
            }
        }
        return matchedResp;
    }

    private static int checkBodyAndHeaders(CoapPacket request, int currRank, Map.Entry<CoapPacket, StubResponse> rule,
            ExHeaderOptions ruleHead) throws NotMatchedException {
        //check body
        if (request.headers().getBlock2Res() != null) {
            //do not check payload for block response request
        } else {
            currRank += match(rule.getKey().getPayload(), request.getPayload()) * 10; //payload has high rank
        }
        //check headers
        currRank += match(ruleHead.getContentFormat(), request.headers().getContentFormat());
        currRank += match(ruleHead.getMaxAge(), request.headers().getMaxAge());
        currRank += match(ruleHead.getObserve(), request.headers().getObserve());
        //currRank += match(ruleHead.getToken(), request.headers().getToken());
        return currRank;
    }

    private static boolean isEmpty(Object ob) {
        return ob == null || (ob instanceof byte[] && ((byte[]) ob).length == 0);
    }

    private static int match(Object required, Object match) throws NotMatchedException {
        //is required
        if (isEmpty(required)) {
            return 0;
        }
        if (checkIfAny(required, match)) {
            return 1;
        }

        if (required.equals(match) || (required instanceof byte[] && match instanceof byte[]
                && Arrays.equals((byte[]) required, (byte[]) match))) {
            return 10;
        }
        throw new NotMatchedException();
    }

    private static boolean checkIfAny(Object required, Object match) throws NotMatchedException {
        if (required == ANY_INTEGER || required == ANY_BYTEARR || required == ANY_LONG || required == ANY_SHORT) { //NOPMD it is ment to compare references
            if (!isEmpty(match)) {
                return true;
            }
            throw new NotMatchedException();
        }
        return false;
    }

    void add(CoapPacket req, StubResponse resp) {
        rules.put(req, resp);
    }

    public StubResourceBuilder when(String uriPath) {
        return new StubResourceBuilder(this, uriPath, Method.GET);
    }

    public StubResourceBuilder whenDELETE(String uriPath) {
        return new StubResourceBuilder(this, uriPath, Method.DELETE);
    }

    public StubResourceBuilder whenPOST(String uriPath) {
        return new StubResourceBuilder(this, uriPath, Method.POST);
    }

    public StubResourceBuilder whenPUT(String uriPath) {
        return new StubResourceBuilder(this, uriPath, Method.PUT);
    }

    public CoapPacket verify(String uriPath) {
        synchronized (requests) {
            return requests.get("GET " + uriPath);
        }
    }

    public CoapPacket verify(String uriPath, int timeoutSec) {
        return requestsGetOrWait("GET " + uriPath, timeoutSec);
    }

    public CoapPacket verifyPOST(String uriPath) {
        synchronized (requests) {
            return requests.get("POST " + uriPath);
        }
    }

    public CoapPacket verifyPOST(String uriPath, int timeoutSec) {
        return requestsGetOrWait("POST " + uriPath, timeoutSec);
    }

    public CoapPacket verifyPUT(String uriPath) {
        synchronized (requests) {
            return requests.get("PUT " + uriPath);
        }
    }

    public CoapPacket verifyPUT(String uriPath, int timeoutSec) {
        return requestsGetOrWait("PUT " + uriPath, timeoutSec);
    }

    public CoapPacket verifyDELETE(String uriPath) {
        synchronized (requests) {
            return requests.get("DELETE " + uriPath);
        }
    }

    public CoapPacket verifyDELETE(String uriPath, int timeoutSec) {
        return requestsGetOrWait("DELETE " + uriPath, timeoutSec);
    }

    private CoapPacket requestsGetOrWait(String key, int timeoutSec) {
        long tsTimeout = System.currentTimeMillis() + timeoutSec * 1000;
        synchronized (requests) {
            CoapPacket v = null;
            v = requests.get(key);
            try {
                while (v == null) {
                    long t = tsTimeout - System.currentTimeMillis();
                    if (t <= 0) {
                        return null;
                    }
                    requests.wait(tsTimeout - System.currentTimeMillis());
                    v = requests.get(key);
                }
            } catch (InterruptedException ex) {
                return null;
            }
            return v;
        }
    }

    public void reset() {
        this.rules.clear();
        synchronized (requests) {
            this.requests.clear();
        }
    }

    public int getLocalPort() {
        return server.getLocalSocketAddress().getPort();
    }

    private static class NotMatchedException extends Exception {

        public NotMatchedException() {
            super("Not matched");
        }
    }

    public void enableObservationHandler() {
        notifQueue = new LinkedBlockingQueue<>();
        server.setObservationHandler(new ObservationHandler() {
            @Override
            public void callException(Exception ex) {
                ex.printStackTrace();
            }

            @Override
            public void call(CoapExchange t) {
                notifQueue.add(t.getRequest());
                t.sendResponse();
            }

            @Override
            public boolean hasObservation(byte[] token) {
                return true;
            }
        });
    }

    public BlockingQueue<CoapPacket> getNotifQueue() {
        return notifQueue;
    }

    static class StubResponse {

        CoapPacket resp;
        long delayMilli;
        boolean delayNotif;

        public StubResponse(CoapPacket resp) {
            this.resp = resp;
        }

        public StubResponse(CoapPacket resp, long delayMilli, boolean delayNotif) {
            this.resp = resp;
            this.delayMilli = delayMilli;
            this.delayNotif = delayNotif;
        }
    }
}
