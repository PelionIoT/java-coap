/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.mbed.coap.exception.CoapTimeoutException;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.utils.Callback;

/**
 * @author szymon
 */
class NotificationAckCallback implements Callback<CoapPacket> {

    private static final Logger LOGGER = Logger.getLogger(NotificationAckCallback.class.getName());
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
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("Notification reset response [" + resp + "]");
            }
            deliveryListener.onFail(sub.getAddress());
        } else {
            //unexpected notification
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("Notification response with unexpected message type: " + resp.getMessageType());
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
            LOGGER.warning("Notification response timeout: " + sub.getAddress().toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Notification response unexpected exception: " + e.getMessage(), e);
        }
        deliveryListener.onFail(sub.getAddress());
    }

}
