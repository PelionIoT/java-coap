/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.observe;

import org.mbed.coap.utils.HexArray;
import java.net.InetSocketAddress;

/**
 *
 * @author szymon
 */
public final class ObservationRelation {

    private final byte[] token;
    private final InetSocketAddress observerAdr;
    private int observeSeq;
    private boolean isDelivering;
    private final boolean isConfirmable;
    private boolean isAutoRemovable = true;

    public ObservationRelation(byte[] token, InetSocketAddress observer, int observeSeq, boolean isConfirmable) {
        this.token = token;
        this.observerAdr = observer;
        this.observeSeq = observeSeq;
        this.isConfirmable = isConfirmable;
    }

    byte[] getToken() {
        return token;
    }

    InetSocketAddress getAddress() {
        return observerAdr;
    }

    synchronized Integer getNextObserveSeq() {
        this.observeSeq = 0xFFFF & (this.observeSeq + 1);
        return this.observeSeq;
    }

    synchronized int getObserveSeq() {
        return this.observeSeq;
    }

    synchronized void setIsDelivering(boolean isDelivering) {
        this.isDelivering = isDelivering;
    }

    synchronized boolean isDelivering() {
        return isDelivering;
    }

    boolean isAutoRemovable() {
        return isAutoRemovable;
    }

    public void setIsAutoRemovable(boolean isAutoRemovable) {
        this.isAutoRemovable = isAutoRemovable;
    }

    boolean getIsConfirmable() {
        return isConfirmable;
    }

    @Override
    public String toString() {
        return "#" + HexArray.toHex(token) + " " + observerAdr.toString();
    }

}
