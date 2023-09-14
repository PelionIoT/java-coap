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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.CoapPacket;
import java.net.InetSocketAddress;

/**
 * Identifies CoAP transaction
 */
public class CoapTransactionId {

    private final int messageId;
    protected InetSocketAddress address;

    public CoapTransactionId(CoapPacket packet) {
        messageId = packet.getMessageId();
        address = packet.getRemoteAddress();
    }

    public boolean matches(CoapTransaction coapTransaction) {
        return coapTransaction.isActive() && this.equals(coapTransaction.getTransactionId());
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
