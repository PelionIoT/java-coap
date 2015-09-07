/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import java.net.InetSocketAddress;

/**
 * NULL implementation for interface NotificationDeliveryListener.
 *
 * @author szymon
 */
class NotificationDeliveryListenerNULL implements NotificationDeliveryListener {

    @Override
    public void onSuccess(InetSocketAddress destinationAddress) {
        //do nothing
    }

    @Override
    public void onFail(InetSocketAddress destinationAddress) {
        //do nothing
    }

    @Override
    public void onNoObservers() {
        //do nothing
    }
}
