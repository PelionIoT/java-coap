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

import com.mbed.coap.packet.BlockSize;

/**
 * Created by olesmi01 on 26.07.2017.
 * Capabilities And Settings POJO for CoAP over TCP https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09
 */
public class CoapTcpCSM {
    // see https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.3 for base values (5.3.1, 5.3.2)
    private static final int BASE_MAX_MESSAGE_SIZE = 1152;
    private static final boolean BASE_BLOCKWISE = false;
    public static final CoapTcpCSM BASE = new CoapTcpCSM(BASE_MAX_MESSAGE_SIZE, BASE_BLOCKWISE);

    private final boolean blockwiseTransfer;
    private final long maxMessageSize;

    public static CoapTcpCSM min(CoapTcpCSM cap1, CoapTcpCSM cap2) {
        return new CoapTcpCSM(
                Math.min(cap1.getMaxMessageSizeInt(), cap2.getMaxMessageSizeInt()),
                cap1.blockwiseTransfer && cap2.blockwiseTransfer
        );
    }

    public CoapTcpCSM(long maxMessageSize, boolean blockwiseTransfer) {
        this.maxMessageSize = maxMessageSize;
        this.blockwiseTransfer = blockwiseTransfer;
    }

    public CoapTcpCSM withNewOptions(Long maxMessageSize, Boolean blockwiseTransfer) {
        return new CoapTcpCSM(
                maxMessageSize != null ? maxMessageSize : this.maxMessageSize,
                blockwiseTransfer != null ? blockwiseTransfer : this.blockwiseTransfer
        );
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

    public BlockSize getBlockSize() {
        if (isBERTEnabled()) {
            return BlockSize.S_1024_BERT;
        }

        if (isBlockTransferEnabled()) {

            if (maxMessageSize >= 1024) {
                return BlockSize.S_1024;
            } else if (maxMessageSize >= 512) {
                return BlockSize.S_512;
            } else if (maxMessageSize >= 256) {
                return BlockSize.S_256;
            } else if (maxMessageSize >= 128) {
                return BlockSize.S_128;
            } else if (maxMessageSize >= 64) {
                return BlockSize.S_64;
            } else if (maxMessageSize >= 32) {
                return BlockSize.S_32;
            } else {
                return BlockSize.S_16;
            }

        }
        return null; // no block transfers enabled for connection
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

    @Override
    public String toString() {
        return "CoapTcpCSM{block=" + blockwiseTransfer + ", size=" + maxMessageSize + '}';
    }

}
