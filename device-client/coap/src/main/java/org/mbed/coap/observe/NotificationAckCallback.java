/*
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import org.mbed.coap.CoapPacket;
import org.mbed.coap.MessageType;
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
    private final NotificationDeliveryListener deliveryListener;
    private final AbstractObservableResource observableResource;

    NotificationAckCallback(ObservationRelation sub, NotificationDeliveryListener deliveryListener,
            AbstractObservableResource observableResource) {
        this.observableResource = observableResource;
        this.sub = sub;
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
