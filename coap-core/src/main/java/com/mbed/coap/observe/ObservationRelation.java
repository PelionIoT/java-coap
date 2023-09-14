/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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

import com.mbed.coap.utils.HexArray;
import java.net.InetSocketAddress;

/**
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

    public synchronized boolean isDelivering() {
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
