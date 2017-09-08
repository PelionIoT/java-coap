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

    public boolean hasRemoteAddress(InetSocketAddress adr) {
        return source.equals(adr);
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
