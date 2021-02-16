/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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

import java.net.InetSocketAddress;
import java.util.Objects;

public class CoapRequestId {

    private final int mid;
    private final InetSocketAddress sourceAddress;
    private final transient long createdTimestampMillis;

    public CoapRequestId(int mid, InetSocketAddress sourceAddress) {
        this.mid = mid;
        this.sourceAddress = sourceAddress;
        this.createdTimestampMillis = System.currentTimeMillis();
    }

    public long getCreatedTimestampMillis() {
        return createdTimestampMillis;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        CoapRequestId objRequestId = (CoapRequestId) obj;

        if (mid != objRequestId.mid) {
            return false;
        }
        return Objects.equals(sourceAddress, objRequestId.sourceAddress);
    }

    @Override
    public int hashCode() {
        int result = mid;
        result = 31 * result + (sourceAddress != null ? sourceAddress.hashCode() : 0);
        return result;
    }
}
