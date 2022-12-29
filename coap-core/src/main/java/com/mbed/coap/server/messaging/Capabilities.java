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
package com.mbed.coap.server.messaging;

import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.Opaque;

/**
 * Capabilities And Settings POJO for CoAP over TCP https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09
 */
public class Capabilities {
    // see https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.3 for base values (5.3.1, 5.3.2)
    private static final int BASE_MAX_MESSAGE_SIZE = 1152;
    private static final boolean BASE_BLOCKWISE = false;
    public static final Capabilities BASE = new Capabilities(BASE_MAX_MESSAGE_SIZE, BASE_BLOCKWISE);

    private final boolean blockwiseTransfer;
    private final long maxMessageSize;

    public static Capabilities min(Capabilities cap1, Capabilities cap2) {
        return new Capabilities(
                Math.min(cap1.getMaxMessageSizeInt(), cap2.getMaxMessageSizeInt()),
                cap1.blockwiseTransfer && cap2.blockwiseTransfer
        );
    }

    public Capabilities(long maxMessageSize, boolean blockwiseTransfer) {
        this.maxMessageSize = maxMessageSize;
        this.blockwiseTransfer = blockwiseTransfer;
    }

    public Capabilities withNewOptions(Long maxMessageSize, Boolean blockwiseTransfer) {
        return new Capabilities(
                maxMessageSize != null ? maxMessageSize : this.maxMessageSize,
                blockwiseTransfer != null ? blockwiseTransfer : this.blockwiseTransfer
        );
    }

    public boolean isBlockTransferEnabled() {
        return blockwiseTransfer;
    }

    public boolean useBlockTransfer(Opaque payload) {
        return blockwiseTransfer &&
                payload != null &&
                payload.size() > getMaxOutboundPayloadSize();

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

    public int getMaxOutboundPayloadSize() {
        BlockSize blockSize = getBlockSize();
        if (blockSize == null) {
            // no blocking, just maximum packet size
            // constant for UDP based (independently of address)
            // taken from CSMStorage for CoAP/TCP (TLS) based on endpoint address
            return getMaxMessageSizeInt();
        }

        if (!blockSize.isBert()) {
            // non-BERT blocking, return just block size
            return blockSize.getSize();
        }

        // BERT, magic starts here
        // block size always 1k in BERT, but take it from enum
        int maxBertBlocksCount = blockSize.numberOfBlocksPerMessage(getMaxMessageSizeInt());
        if (maxBertBlocksCount > 1) {
            // leave minimum 1k room for options if maxMessageSize is in 1k blocks
            return (maxBertBlocksCount - 1) * blockSize.getSize();
        } else {
            // block size is 1k, minimum BERT message size is 1152 so we have room for options
            return blockSize.getSize();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Capabilities that = (Capabilities) o;

        if (blockwiseTransfer != that.blockwiseTransfer) {
            return false;
        }
        return maxMessageSize == that.maxMessageSize;
    }

    @Override
    public int hashCode() {
        int result = blockwiseTransfer ? 1 : 0;
        result = 31 * result + (int) (maxMessageSize ^ (maxMessageSize >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "CoapTcpCSM{block=" + blockwiseTransfer + ", size=" + maxMessageSize + '}';
    }

}
