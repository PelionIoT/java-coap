/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
package com.mbed.coap.server.block;

import java.net.InetSocketAddress;
import java.util.Objects;

class BlockRequestId {

    private final String uriPath;
    private final InetSocketAddress sourceAddress;

    public BlockRequestId(String uriPath, InetSocketAddress sourceAddress) {
        this.uriPath = uriPath;
        this.sourceAddress = sourceAddress;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + (this.uriPath != null ? this.uriPath.hashCode() : 0);
        hash = 73 * hash + (this.sourceAddress != null ? this.sourceAddress.hashCode() : 0);
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
        final BlockRequestId other = (BlockRequestId) obj;
        if (!Objects.equals(this.uriPath, other.uriPath)) {
            return false;
        }
        return Objects.equals(this.sourceAddress, other.sourceAddress);
    }

}
