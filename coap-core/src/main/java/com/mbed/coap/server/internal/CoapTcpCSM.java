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

import java.util.Optional;

/**
 * Created by olesmi01 on 26.07.2017.
 * Capabilities And Settings POJO for CoAP over TCP https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09
 */
public class CoapTcpCSM {
    public static final CoapTcpCSM BASE = new CoapTcpCSM();

    // see https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.3 for base values (5.3.1, 5.3.2)
    private static final int BASE_MAX_MESSAGE_SIZE = 1152;
    private static final boolean BASE_BLOCKWISE = false;

    private final boolean blockwiseTransfer;
    private final long maxMessageSize;

    private CoapTcpCSM() {
        blockwiseTransfer = BASE_BLOCKWISE;
        maxMessageSize = BASE_MAX_MESSAGE_SIZE;
    }

    private CoapTcpCSM(long maxMessageSize, boolean blockwiseTransfer) {
        this.maxMessageSize = maxMessageSize;
        this.blockwiseTransfer = blockwiseTransfer;
    }

    public CoapTcpCSM withNewOptions(Long maxMessageSize, Boolean blockwiseTransfer) {
        long newMaxSize = Optional.ofNullable(maxMessageSize).orElse(this.maxMessageSize);
        boolean newBlockWise = Optional.ofNullable(blockwiseTransfer).orElse(this.blockwiseTransfer);

        if (newMaxSize == BASE.maxMessageSize
                && newBlockWise == BASE.blockwiseTransfer) {
            return BASE;
        }

        return new CoapTcpCSM(newMaxSize, newBlockWise);
    }

    public boolean isBlockTransferEnabled() {
        return blockwiseTransfer;
    }

    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    public int getMaxMessageSizeInt() {
        return (int) Math.min(maxMessageSize, Integer.MAX_VALUE);
    }

    public boolean isBERTEnabled() {
        return blockwiseTransfer && maxMessageSize > BASE_MAX_MESSAGE_SIZE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CoapTcpCSM that = (CoapTcpCSM) o;

        if (blockwiseTransfer != that.blockwiseTransfer) {
            return false;
        }
        return maxMessageSize == that.maxMessageSize;
    }

    @Override
    public int hashCode() {
        int result = (blockwiseTransfer ? 1 : 0);
        result = 31 * result + (int) (maxMessageSize ^ (maxMessageSize >>> 32));
        return result;
    }
}
