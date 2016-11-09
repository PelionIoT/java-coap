/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.packet.BlockOption;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.utils.Callback;
import org.mbed.coap.utils.CoapResource;
import org.mbed.coap.utils.HexArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
public abstract class AbstractObservableResource extends CoapResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractObservableResource.class);
    protected CoapServer coapServer;
    protected final Map<InetSocketAddress, ObservationRelation> obsRelations = Collections.synchronizedMap(new HashMap<InetSocketAddress, ObservationRelation>());
    protected boolean removeIfNoObsHeader;
    private final int FORCE_CON_FREQ;
    private final static int DEFAULT_FORCE_CON_FREQ = 20;
    private Boolean conNotifications;
    protected static final NotificationDeliveryListener DELIVERY_LISTENER_NULL = new NotificationDeliveryListenerNULL();

    public AbstractObservableResource(CoapServer coapServer) {
        this(coapServer, Boolean.TRUE, DEFAULT_FORCE_CON_FREQ);
    }

    public AbstractObservableResource(CoapServer coapServer, boolean includeObservableFlag) {
        this(coapServer, includeObservableFlag, DEFAULT_FORCE_CON_FREQ);
    }

    public AbstractObservableResource(CoapServer coapServer, boolean includeObservableFlag, int forceConFreq) {
        this.coapServer = coapServer;
        if (includeObservableFlag) {
            getLink().setObservable(true);
        }
        this.FORCE_CON_FREQ = forceConFreq;
    }

    public void setConNotifications(boolean conNotifications) {

        synchronized (obsRelations) {
            this.conNotifications = conNotifications;
        }
    }

    @Override
    public void handle(CoapExchange exchange) throws CoapException {
        switch (exchange.getRequestMethod()) {
            case POST:
                post(exchange);
                break;
            case GET:
                if (addObserver(exchange)) {
                    get(exchange);
                }
                break;
            case PUT:
                put(exchange);
                break;
            case DELETE:
                delete(exchange);
                break;
            default:
                throw new RuntimeException();
        }
    }

    protected boolean addObserver(CoapExchange exchange) {
        CoapPacket request = exchange.getRequest();
        if (request.headers().getObserve() == null) {

            if (request.headers().getBlock2Res() == null && request.headers().getBlock1Req() == null
                    && removeIfNoObsHeader && obsRelations.remove(exchange.getRemoteAddress()) != null
                    && LOGGER.isTraceEnabled()) {
                LOGGER.trace("Observation removed: " + exchange.getRemoteAddress());
            }
            return true;
        }

        if (request.getToken() == null) {
            LOGGER.warn("Observation registration without token, ignoring " + request);
            exchange.sendResetResponse();
            return false;
        }
        if (request.headers().getBlock2Res() == null && request.headers().getBlock1Req() == null) {

            ObservationRelation subs = new ObservationRelation(request.getToken(), request.getRemoteAddress(), request.headers().getObserve(), request.getMustAcknowledge());

            addObservationRelation(subs, request.headers().getUriPath());
            exchange.getResponseHeaders().setObserve(subs.getObserveSeq());
            exchange.setResponseToken(subs.getToken());
        } else {
            exchange.getResponseHeaders().setObserve(request.headers().getObserve());
            exchange.setResponseToken(request.getToken());
        }
        return true;
    }

    protected void addObservationRelation(ObservationRelation subs, String uriPath) {
        //check for existing relations
        for (ObservationRelation rel : obsRelations.values()) {
            if (rel.getAddress().equals(subs.getAddress())) {
                if (Arrays.equals(rel.getToken(), subs.getToken())) {
                    LOGGER.warn("Adding different observation from same ip [" + rel.getAddress() + "] on " + uriPath + ", token: 0x" + HexArray.toHex(rel.getToken()));
                } else {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Updating observation from ip [" + rel.getAddress() + "] on " + uriPath);
                    }
                }
            }
        }
        obsRelations.put(subs.getAddress(), subs);
    }

    /**
     * Terminates all observations sending RESET notification to all observers.
     *
     * @throws org.mbed.coap.exception.CoapException coap exception
     */
    public final void notifyTermination() throws CoapException {
        notifyTermination(null);
    }

    /**
     * Terminates all observations sending error notification to all observers.
     *
     * @param code error code
     * @throws org.mbed.coap.exception.CoapException coap exception
     */
    public final void notifyTermination(Code code) throws CoapException {

        Iterator<Map.Entry<InetSocketAddress, ObservationRelation>> iter = obsRelations.entrySet().iterator();

        synchronized (obsRelations) {
            while (iter.hasNext()) {
                Map.Entry<InetSocketAddress, ObservationRelation> entry = iter.next();

                ObservationRelation sub = entry.getValue();

                CoapPacket coapNotif = new CoapPacket(sub.getAddress());
                coapNotif.setCode(code);
                coapNotif.headers().setObserve(sub.getNextObserveSeq()); //TODO: sync problem
                coapNotif.setToken(sub.getToken());

                if (code == null) {
                    coapNotif.setMessageType(MessageType.Reset);
                } else {
                    coapNotif.setMessageType(sub.getIsConfirmable() ? MessageType.Confirmable : MessageType.NonConfirmable);
                }

                this.coapServer.makeRequest(coapNotif, Callback.ignore());

                //remove subscriber
                iter.remove();
            }
        }
    }

    protected final void notifyChange(byte[] payload, Short contentType) throws CoapException {
        notifyChange(payload, contentType, null, null, DELIVERY_LISTENER_NULL);
    }

    protected final void notifyChange(byte[] payload, Short contentType, byte[] etag) throws CoapException {
        notifyChange(payload, contentType, etag, null, DELIVERY_LISTENER_NULL);
    }

    /**
     * Notify change to all observers
     *
     * @param payload payload
     * @param contentType content type
     * @param etag etag
     * @param maxAge max age
     * @param deliveryListener notification delivery listener
     * @throws CoapException coap exception
     */
    protected final void notifyChange(byte[] payload, Short contentType, byte[] etag, Long maxAge, NotificationDeliveryListener deliveryListener) throws CoapException {
        if (deliveryListener == null) {
            throw new NullPointerException();
        }
        Iterator<Map.Entry<InetSocketAddress, ObservationRelation>> iter = obsRelations.entrySet().iterator();
        synchronized (obsRelations) {
            if (!iter.hasNext()) {
                deliveryListener.onNoObservers();
                return;
            }
            while (iter.hasNext()) {
                Map.Entry<InetSocketAddress, ObservationRelation> entry = iter.next();

                ObservationRelation sub = entry.getValue();
                boolean isConfirmable = conNotifications == null ? sub.getIsConfirmable() : conNotifications;
                CoapPacket coapNotif = createNotifPacket(sub, payload, contentType, etag, maxAge);

                if (!sub.isDelivering()) {
                    sendNotification(isConfirmable, sub, coapNotif, deliveryListener);
                } else {
                    LOGGER.warn("Could not deliver notification to " + entry.getKey() + ", previous still not confirmed");
                    deliveryListener.onFail(entry.getKey());
                }
            }
        }
    }

    private void sendNotification(boolean isConfirmable, ObservationRelation sub, CoapPacket coapNotif,
            NotificationDeliveryListener deliveryListener) throws CoapException {

        if (isConfirmable || (sub.getObserveSeq() % FORCE_CON_FREQ) == 0) {
            coapNotif.setMessageType(MessageType.Confirmable);
            sub.setIsDelivering(true);
            try {
                this.coapServer.makeRequest(coapNotif, new NotificationAckCallback(sub, deliveryListener, this));
            } catch (CoapException exception) {
                sub.setIsDelivering(false);
                throw exception;
            }
        } else {
            coapNotif.setMessageType(MessageType.NonConfirmable);
            this.coapServer.makeRequest(coapNotif, Callback.ignore());
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Sent notification [" + coapNotif.toString() + "]");
        }
    }

    private CoapPacket createNotifPacket(ObservationRelation sub, byte[] payload, Short contentType, byte[] etag, Long maxAge) {
        CoapPacket coapNotif = new CoapPacket(sub.getAddress());
        coapNotif.setCode(Code.C205_CONTENT);
        coapNotif.headers().setObserve(sub.getNextObserveSeq());
        coapNotif.setToken(sub.getToken());
        coapNotif.headers().setEtag(etag);
        coapNotif.headers().setMaxAge(maxAge);

        if (this.coapServer.getBlockSize() != null && payload.length > this.coapServer.getBlockSize().getSize()) {
            BlockOption blockOpt = new BlockOption(0, this.coapServer.getBlockSize(), true);
            coapNotif.headers().setBlock2Res(blockOpt);
            coapNotif.setPayload(blockOpt.createBlockPart(payload));
        } else {
            coapNotif.setPayload(payload);
        }
        if (contentType != null && contentType > -1) {
            coapNotif.headers().setContentFormat(contentType);
        }

        return coapNotif;
    }

    protected final void removeSubscriber(ObservationRelation sub) {
        if (sub.isAutoRemovable() && obsRelations.remove(sub.getAddress()) != null) {
            LOGGER.info("Observation removed [" + sub + "]");
        }
    }
}
