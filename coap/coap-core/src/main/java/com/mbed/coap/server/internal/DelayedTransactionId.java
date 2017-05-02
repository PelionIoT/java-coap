/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.server.internal;

import com.mbed.coap.utils.HexArray;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * @author szymon
 */
public class DelayedTransactionId {
    private final byte[] token;
    private final InetSocketAddress source;

    public DelayedTransactionId(byte[] token, InetSocketAddress source) {
        this.token = token;
        this.source = source;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DelayedTransactionId other = (DelayedTransactionId) obj;
        if (!Arrays.equals(this.token, other.token)) {
            return false;
        }
        if (this.source != other.source && (this.source == null || !this.source.equals(other.source))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Arrays.hashCode(this.token);
        hash = 41 * hash + (this.source != null ? this.source.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return HexArray.toHex(token) + "#" + source.toString();
    }

}
