/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import java.net.InetSocketAddress;

/**
 * Listener interface for notification delivery status.
 *
 * @author szymon
 */
public interface NotificationDeliveryListener {

    /**
     * Provides destination IP address of notification that was successfully
     * delivered.
     *
     * @param destinationAddress destination address
     */
    void onSuccess(InetSocketAddress destinationAddress);

    /**
     * Provides destination IP address of notification that failed to deliver.
     *
     * @param destinationAddress destination address
     */
    void onFail(InetSocketAddress destinationAddress);

    /**
     * Calls when there is no observer.
     */
    void onNoObservers();
}
