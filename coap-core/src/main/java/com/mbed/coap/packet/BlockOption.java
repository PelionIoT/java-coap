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
package com.mbed.coap.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * This class implements RFC7959 (Block-Wise Transfers in the Constrained Application Protocol)
 *
 * @author szymon
 */
public final class BlockOption implements Serializable {

    private final int blockNr;
    private final boolean more;
    private final BlockSize blockSize;

    public BlockOption(int blockNr, BlockSize blockSize, boolean more) {
        this.blockNr = blockNr;
        this.blockSize = blockSize;
        this.more = more;
    }

    public BlockOption(byte[] raw) {
        int bl = DataConvertingUtility.readVariableULong(raw).intValue();
        blockNr = bl >> 4;
        more = (bl & 0x8) != 0;
        byte szx = (byte) (bl & 0x07);
        blockSize = BlockSize.fromRawSzx(szx);
    }

    public byte[] toBytes() {
        int block = blockNr << 4;
        if (more) {
            block |= 1 << 3;
        }
        block |= blockSize.toRawSzx();
        return DataConvertingUtility.convertVariableUInt(block);
    }

    /**
     * @return the blockNr
     */
    public int getNr() {
        return blockNr;
    }

    public BlockSize getBlockSize() {
        return blockSize;
    }

    public boolean isBert() {
        return blockSize.bert;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return blockSize.getSize();
    }

    public boolean hasMore() {
        return more;
    }

    /**
     * Creates next block option instance with incremented block number. Set
     * more flag according to payload size.
     *
     * @param fullPayload full payload
     * @return BlockOption
     */
    public BlockOption nextBlock(byte[] fullPayload) {
        return nextBertBlock(fullPayload, 1, getSize());
    }

    /**
     * Creates next block option
     *
     * @param fullPayload - full payload to be sent through block transfer
     * @param lastBlocksCountPerMessage - number of BERT 1k sub-blocks in last sent block
     * @param maxPayloadSizePerBlock - max payload size per BERT block
     * @return nex block option instance
     */
    public BlockOption nextBertBlock(byte[] fullPayload, int lastBlocksCountPerMessage, int maxPayloadSizePerBlock) {

        int nextBlockNumber = blockNr + (isBert()
                ? lastBlocksCountPerMessage
                : 1);
        int nextPayloadPos = nextBlockNumber * getSize();
        int leftPayload = fullPayload.length - nextPayloadPos;

        boolean newHasMore = isBert()
                ? leftPayload > maxPayloadSizePerBlock
                : leftPayload > getSize();

        return new BlockOption(nextBlockNumber, blockSize, newHasMore);
    }

    public int appendPayload(ByteArrayOutputStream origPayload, byte[] block) {
        try {
            origPayload.write(block);
        } catch (IOException e) {
            // should never happen
            throw new RuntimeException("Can't append payload to buffer", e);
        }
        return block.length / getSize(); // return count of blocks added, needed for BERT
    }

    /**
     * Creates new block, saves it into outputBlock and retuns count of block put to the payload according
     * to maxPayloadSizePerBlock
     *
     * @param fullPayload - full payload from which block will be created
     * @param outputBlock - outputStream where block will be saved
     * @param maxPayloadSizePerBlock - maximum payload size (mostly for BERT blocks)
     * @return
     */
    public int createBlockPart(byte[] fullPayload, ByteArrayOutputStream outputBlock, int maxPayloadSizePerBlock) {
        //block size 16
        //b0: 0 - 15
        //b1: 16 - 31

        int startPos = blockNr * getSize();
        if (startPos > fullPayload.length - 1) {
            //payload too small
            return 0;
        }

        int blocksCount = isBert()
                ? maxPayloadSizePerBlock / getSize()
                : 1;

        // maxPayloadSize is not used to round len to blockSize
        // by default, maxPayloadSizePerBlock usually should be rounded to n*blockSize
        int len = getSize() * blocksCount;
        if (startPos + len > fullPayload.length) {
            len = fullPayload.length - startPos;
            assert !hasMore();
        }
        outputBlock.write(fullPayload, startPos, len);

        return blocksCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockOption)) {
            return false;
        }
        if (obj.hashCode() != this.hashCode()) {
            return false;
        }

        return ((BlockOption) obj).blockSize == this.blockSize // enum value comparison
                && ((BlockOption) obj).blockNr == this.blockNr
                && ((BlockOption) obj).more == this.more;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + this.blockNr;
        hash = 67 * hash + this.blockSize.szx;
        hash = 67 * hash + (this.blockSize.bert ? 1 : 0);
        hash = 67 * hash + (this.more ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.blockNr);
        sb.append('|').append(more ? "more" : "last");
        if (isBert()) {
            sb.append("|BERT");
        } else {
            sb.append('|').append(getSize());
        }
        return sb.toString();
    }
}
