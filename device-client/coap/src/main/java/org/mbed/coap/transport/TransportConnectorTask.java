/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

/**
 *
 * @author szymon
 */
public interface TransportConnectorTask extends TransportConnector {
//TODO: rename to TransportConnectorReceivable

    /**
     * Performs receive operation, if new message is available then calls
     * TransportHandler.onReceive. Implementation should perform until there is
     * no new messages and then return (in no blocking mode).
     *
     */
    void performReceive();
}
