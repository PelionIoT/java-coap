/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import java.net.InetSocketAddress;
import org.mbed.coap.CoapPacket;

/**
 * Identifies CoAP transaction
 */
public class CoapTransactionId {

    protected int messageId;
    protected InetSocketAddress address;

    public CoapTransactionId(CoapPacket packet) {
        messageId = packet.getMessageId();
        address = packet.getOtherEndAddress();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + this.messageId;
        hash = 67 * hash + (this.address != null ? this.address.hashCode() : 0);
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
        final CoapTransactionId other = (CoapTransactionId) obj;
        if (this.messageId != other.messageId) {
            return false;
        }
        if (this.address != other.address && (this.address == null || !this.address.equals(other.address))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return messageId + "#" + address.toString();
    }

    public InetSocketAddress getAddress() {
        return address;
    }

}
