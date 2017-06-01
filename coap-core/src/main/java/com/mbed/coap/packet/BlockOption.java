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

import java.io.Serializable;
import java.util.Arrays;

/**
 * This class implements RFC7959 (Block-Wise Transfers in the Constrained Application Protocol)
 *
 * @author szymon
 */
public final class BlockOption implements Serializable {

    private final int blockNr;
    private final byte szx;
    private final boolean more;

    public BlockOption(int blockNr, BlockSize blockSize, boolean more) {
        this.blockNr = blockNr;
        this.szx = blockSize.szx;
        this.more = more;
    }

    public BlockOption(int blockNr, byte szx, boolean more) {
        this.blockNr = blockNr;
        this.szx = szx;
        this.more = more;
    }

    public BlockOption(byte[] raw) {
        int bl = DataConvertingUtility.readVariableULong(raw).intValue();
        blockNr = bl >> 4;
        more = (bl & 0x8) != 0;
        szx = (byte) (bl & 0x7);
    }

    public byte[] toBytes() {
        int block = blockNr << 4;
        if (more) {
            block |= 1 << 3;
        }
        block |= szx;
        return DataConvertingUtility.convertVariableUInt(block);
    }

    /**
     * @return the blockNr
     */
    public int getNr() {
        return blockNr;
    }

    public byte getSzx() {
        return szx;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return 1 << (szx + 4);
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
        if (fullPayload.length > (blockNr + 2) * getSize()) {
            //has more
            return new BlockOption(blockNr + 1, szx, true);
        } else {
            return new BlockOption(blockNr + 1, szx, false);
        }

    }

    public byte[] appendPayload(byte[] origPayload, byte[] block) {
        int size = blockNr * getSize() + block.length;
        byte[] retPayload;
        if (origPayload.length < size) {
            //retPayload = new byte[size];
            retPayload = Arrays.copyOf(origPayload, size);
        } else {
            retPayload = origPayload;
        }
        System.arraycopy(block, 0, retPayload, blockNr * getSize(), block.length);
        //        for (int i=0;i<block.length;i++){
        //            retPayload[i+blockNr*getSize()] = block[i];
        //        }
        //LOGGER.trace("appendPayload() origPayload-len: " + origPayload.length + " block-len: " +block.length + " size: " + size +  " nr: " + blockNr );
        return retPayload;
    }

    public byte[] createBlockPart(byte[] fullPayload) {
        //block size 16
        //b0: 0 - 15
        //b1: 16 - 31

        int startPos = blockNr * getSize();
        if (startPos > fullPayload.length - 1) {
            //payload to small
            return null;
        }
        int len = getSize();
        if (startPos + len > fullPayload.length) {
            len = fullPayload.length - startPos;
        }
        byte[] nwPayload = new byte[len];
        System.arraycopy(fullPayload, startPos, nwPayload, 0, len);
        //LOGGER.trace("createBlockPart() payload-len: " + fullPayload.length + " start: " +startPos + " len: " + len);
        return nwPayload;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockOption)) {
            return false;
        }
        if (obj.hashCode() != this.hashCode()) {
            return false;
        }

        return ((BlockOption) obj).szx == this.szx
                && ((BlockOption) obj).blockNr == this.blockNr
                && ((BlockOption) obj).more == this.more;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + this.blockNr;
        hash = 67 * hash + this.szx;
        hash = 67 * hash + (this.more ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.blockNr);
        sb.append('|').append(more ? "more" : "last");
        sb.append('|').append(getSize());
        return sb.toString();
    }
}
