/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import org.mbed.coap.BlockOption;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.MessageType;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.utils.CoapCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author szymon
 */
class NotificationAckCallback implements CoapCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationAckCallback.class);
    private final ObservationRelation sub;
    private final CoapPacket coapNotif;
    private final byte[] payload;
    private final NotificationDeliveryListener deliveryListener;
    private final AbstractObservableResource observableResource;

    NotificationAckCallback(ObservationRelation sub, CoapPacket coapNotif, byte[] payload,
            NotificationDeliveryListener deliveryListener, final AbstractObservableResource observableResource) {
        this.observableResource = observableResource;
        this.sub = sub;
        this.coapNotif = coapNotif;
        this.payload = payload;
        this.sub.setIsDelivering(true);
        if (deliveryListener == null) {
            throw new NullPointerException();
        }
        this.deliveryListener = deliveryListener;
    }

    @Override
    public void call(CoapPacket resp) {
        this.sub.setIsDelivering(false);
        if (resp.getMessageType() == MessageType.Acknowledgement) {
            //OK
            if (coapNotif != null && coapNotif.headers().getBlock2Res() != null && coapNotif.headers().getBlock2Res().hasMore()) {
                //send next block
                BlockOption blockOpt = coapNotif.headers().getBlock2Res().nextBlock(payload);
                coapNotif.headers().setBlock2Res(blockOpt);
                coapNotif.setPayload(blockOpt.createBlockPart(payload));
                try {
                    observableResource.coapServer.makeRequest(coapNotif, this);
                } catch (CoapException ex) {
                    LOGGER.error(ex.getMessage(), ex);
                }
            }
            deliveryListener.onSuccess(sub.getAddress());
        } else if (resp.getMessageType() == MessageType.Reset) {
            //observation termination
            observableResource.removeSubscriber(sub);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Notification reset response [" + resp + "]");
            }
            deliveryListener.onFail(sub.getAddress());
        } else {
            //unexpected notification
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Notification response with unexpected message type: " + resp.getMessageType());
            }
            deliveryListener.onFail(sub.getAddress());
        }
    }

    @Override
    public void callException(Exception ex) {
        observableResource.removeSubscriber(sub);
        try {
            throw ex;
        } catch (CoapTimeoutException e) {
            //timeout
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Notification response timeout: " + sub.getAddress().toString());
            }
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Notification response unexpected exception: " + e.getMessage(), e);
            }
        }
        deliveryListener.onFail(sub.getAddress());
    }

}
