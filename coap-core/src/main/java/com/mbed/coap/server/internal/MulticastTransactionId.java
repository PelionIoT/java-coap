/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.internal;

import com.mbed.coap.packet.CoapPacket;

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
