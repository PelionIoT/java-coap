/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.coap.observe;

import com.mbed.coap.exception.CoapTimeoutException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.utils.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author szymon
 */
class NotificationAckCallback implements Callback<CoapPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationAckCallback.class.getName());
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
            LOGGER.warn("Notification response timeout: " + sub.getAddress().toString());
        } catch (Exception e) {
            LOGGER.warn("Notification response unexpected exception: " + e.getMessage(), e);
        }
        deliveryListener.onFail(sub.getAddress());
    }

}
