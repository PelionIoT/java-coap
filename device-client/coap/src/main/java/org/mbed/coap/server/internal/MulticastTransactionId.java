/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

import org.mbed.coap.packet.CoapPacket;

/**
 * @author szymon
 */
class MulticastTransactionId extends CoapTransactionId {

    public MulticastTransactionId(CoapPacket coapPacket) {
        super(coapPacket);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CoapTransactionId)) {
            return false;
        }
        final CoapTransactionId other = (CoapTransactionId) obj;
        if (this.messageId != other.messageId) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 8;
        hash = 57 * hash + super.hashCode();
        return hash;
    }

}
